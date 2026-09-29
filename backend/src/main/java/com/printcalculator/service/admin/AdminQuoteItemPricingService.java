package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminQuoteItemStatsDto;
import com.printcalculator.dto.AdminQuoteItemStatsUpdateRequest;
import com.printcalculator.dto.AdminQuoteItemsResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.quote.QuoteSessionResponseAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminQuoteItemPricingService {
    private static final String LINE_ITEM_TYPE_PRINT_FILE = "PRINT_FILE";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_CONVERTED = "CONVERTED";

    private final QuoteSessionRepository sessionRepo;
    private final QuoteLineItemRepository lineItemRepo;
    private final PricingPolicyRepository pricingRepo;
    private final PrinterMachineRepository machineRepo;
    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final QuoteCalculator quoteCalculator;
    private final QuoteSessionTotalsService totalsService;
    private final QuoteSessionResponseAssembler assembler;

    public AdminQuoteItemPricingService(QuoteSessionRepository sessionRepo,
                                        QuoteLineItemRepository lineItemRepo,
                                        PricingPolicyRepository pricingRepo,
                                        PrinterMachineRepository machineRepo,
                                        FilamentMaterialTypeRepository materialRepo,
                                        FilamentVariantRepository variantRepo,
                                        QuoteCalculator quoteCalculator,
                                        QuoteSessionTotalsService totalsService,
                                        QuoteSessionResponseAssembler assembler) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.pricingRepo = pricingRepo;
        this.machineRepo = machineRepo;
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.quoteCalculator = quoteCalculator;
        this.totalsService = totalsService;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public AdminQuoteItemsResponse getItems(UUID sessionId) {
        QuoteSession session = loadSession(sessionId);
        List<QuoteLineItem> items = lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId);
        QuoteSessionTotalsService.QuoteSessionTotals totals = totalsService.compute(session, items);
        return toResponse(session, items, totals, null, null);
    }

    @Transactional
    public AdminQuoteItemsResponse updateStats(UUID sessionId, AdminQuoteItemStatsUpdateRequest request) {
        QuoteSession session = loadSession(sessionId);
        if (STATUS_CONVERTED.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify a converted session");
        }

        List<QuoteLineItem> items = lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId);
        Map<UUID, QuoteLineItem> itemsById = items.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(QuoteLineItem::getId, item -> item, (first, second) -> first));

        PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        if (policy == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active pricing policy found");
        }

        Set<UUID> seenItemIds = new HashSet<>();
        List<ResolvedItem> resolvedItems = new ArrayList<>();
        for (AdminQuoteItemStatsUpdateRequest.Item requestItem : request.getItems()) {
            UUID itemId = requestItem.getItemId();
            if (!seenItemIds.add(itemId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate itemId in payload: " + itemId);
            }

            QuoteLineItem item = itemsById.get(itemId);
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in session");
            }
            if (!LINE_ITEM_TYPE_PRINT_FILE.equals(item.getLineItemType()) || !STATUS_READY.equals(item.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only READY print files can be updated");
            }

            PrinterMachine machine = resolveMachine(item);
            FilamentVariant variant = resolveVariant(item, session);
            BigDecimal newUnitPrice = quoteCalculator.calculatePrintItemUnitPrice(
                            requestItem.getPrintTimeSeconds(),
                            requestItem.getMaterialGrams(),
                            machine,
                            policy,
                            variant
                    )
                    .setScale(2, RoundingMode.HALF_UP);
            resolvedItems.add(new ResolvedItem(
                    item,
                    requestItem.getPrintTimeSeconds(),
                    requestItem.getMaterialGrams(),
                    newUnitPrice
            ));
        }

        if (!request.isPersist()) {
            QuoteSessionTotalsService.QuoteSessionTotals currentTotals = totalsService.compute(session, items);
            Map<UUID, QuoteSessionTotalsService.ItemStatsOverride> overrides = new LinkedHashMap<>();
            for (ResolvedItem resolved : resolvedItems) {
                overrides.put(
                        resolved.item().getId(),
                        new QuoteSessionTotalsService.ItemStatsOverride(
                                resolved.printTimeSeconds(),
                                resolved.unitPrice()
                        )
                );
            }
            QuoteSessionTotalsService.QuoteSessionTotals proposedTotals =
                    totalsService.compute(session, items, overrides);
            return toResponse(session, items, proposedTotals, overrides, currentTotals);
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (ResolvedItem resolved : resolvedItems) {
            QuoteLineItem item = resolved.item();
            Map<String, Object> breakdown = new HashMap<>(
                    item.getPricingBreakdown() != null ? item.getPricingBreakdown() : Map.of()
            );
            breakdown.putIfAbsent("originalPrintTimeSeconds", item.getPrintTimeSeconds());
            breakdown.putIfAbsent("originalMaterialGrams", item.getMaterialGrams());
            breakdown.putIfAbsent("originalUnitPriceChf", item.getUnitPriceChf());
            breakdown.put("printStatsUpdatedAt", now.toString());
            breakdown.keySet().removeIf(key -> key.startsWith("cadColor"));
            breakdown.put("machine_cost", resolved.unitPrice());
            item.setPricingBreakdown(breakdown);
            item.setPrintTimeSeconds(resolved.printTimeSeconds());
            item.setMaterialGrams(resolved.materialGrams());
            item.setUnitPriceChf(resolved.unitPrice());
            item.setUpdatedAt(now);
        }
        lineItemRepo.saveAll(resolvedItems.stream().map(ResolvedItem::item).toList());

        QuoteSessionTotalsService.QuoteSessionTotals updatedTotals = totalsService.compute(session, items);
        return toResponse(session, items, updatedTotals, null, null);
    }

    private QuoteSession loadSession(UUID sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private PrinterMachine resolveMachine(QuoteLineItem item) {
        Long machineId = extractMachineId(item.getPricingBreakdown());
        if (machineId != null) {
            PrinterMachine machine = machineRepo.findById(machineId).orElse(null);
            if (machine != null) {
                return machine;
            }
        }
        return machineRepo.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active printer available"));
    }

    private Long extractMachineId(Map<String, Object> breakdown) {
        if (breakdown == null) {
            return null;
        }
        Object raw = breakdown.get("printerMachineId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private FilamentVariant resolveVariant(QuoteLineItem item, QuoteSession session) {
        if (item.getFilamentVariant() != null) {
            return item.getFilamentVariant();
        }
        String materialCode = item.getMaterialCode() != null && !item.getMaterialCode().isBlank()
                ? item.getMaterialCode()
                : session.getMaterialCode();
        if (materialCode == null || materialCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is unknown for item");
        }
        FilamentMaterialType materialType = materialRepo.findByMaterialCode(materialCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unknown material type: " + materialCode
                ));
        return variantRepo.findFirstByFilamentMaterialTypeAndIsActiveTrue(materialType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No active variant for material: " + materialCode
                ));
    }

    private AdminQuoteItemsResponse toResponse(QuoteSession session,
                                               List<QuoteLineItem> items,
                                               QuoteSessionTotalsService.QuoteSessionTotals totals,
                                               Map<UUID, QuoteSessionTotalsService.ItemStatsOverride> overrides,
                                               QuoteSessionTotalsService.QuoteSessionTotals currentTotals) {
        boolean preview = overrides != null;
        QuoteSessionTotalsService.QuoteSessionTotals referenceTotals = preview ? currentTotals : totals;

        List<AdminQuoteItemStatsDto> itemDtos = new ArrayList<>();
        for (QuoteLineItem item : items) {
            AdminQuoteItemStatsDto dto = new AdminQuoteItemStatsDto();
            dto.setId(item.getId());
            dto.setDisplayName(item.getDisplayName() != null && !item.getDisplayName().isBlank()
                    ? item.getDisplayName()
                    : item.getOriginalFilename());
            dto.setQuantity(item.getQuantity());
            dto.setPrintTimeSeconds(item.getPrintTimeSeconds());
            dto.setMaterialGrams(item.getMaterialGrams());
            dto.setStatus(item.getStatus());
            dto.setLineItemType(item.getLineItemType());
            dto.setEditable(LINE_ITEM_TYPE_PRINT_FILE.equals(item.getLineItemType()) && STATUS_READY.equals(item.getStatus()));
            dto.setUnitPriceChf(assembler.resolveDistributedUnitPrice(
                    item.getUnitPriceChf() != null ? item.getUnitPriceChf() : BigDecimal.ZERO,
                    item.getPrintTimeSeconds(),
                    item.getQuantity(),
                    referenceTotals
            ));

            if (preview) {
                QuoteSessionTotalsService.ItemStatsOverride override = item.getId() != null
                        ? overrides.get(item.getId())
                        : null;
                if (override != null) {
                    dto.setNewUnitPriceChf(assembler.resolveDistributedUnitPrice(
                            override.unitPriceChf(),
                            override.printTimeSeconds(),
                            item.getQuantity(),
                            totals
                    ));
                }
            }
            itemDtos.add(dto);
        }

        AdminQuoteItemsResponse response = new AdminQuoteItemsResponse();
        response.setSessionId(session.getId());
        response.setSessionStatus(session.getStatus());
        response.setItems(itemDtos);
        response.setPrintItemsTotalChf(totals.printItemsTotalChf());
        response.setGlobalMachineCostChf(totals.globalMachineCostChf());
        response.setCadTotalChf(totals.cadTotalChf());
        response.setItemsTotalChf(totals.itemsTotalChf());
        response.setSetupCostChf(totals.setupCostChf());
        response.setShippingCostChf(totals.shippingCostChf());
        response.setGrandTotalChf(totals.grandTotalChf());
        return response;
    }

    private record ResolvedItem(QuoteLineItem item,
                                Integer printTimeSeconds,
                                BigDecimal materialGrams,
                                BigDecimal unitPrice) {
    }
}

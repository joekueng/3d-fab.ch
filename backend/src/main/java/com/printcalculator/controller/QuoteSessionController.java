package com.printcalculator.controller;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.OrcaProfileResolver;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.SlicerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@RestController
@RequestMapping("/api/quote-sessions")

public class QuoteSessionController {
    private static final Path QUOTE_STORAGE_ROOT = Paths.get("storage_quotes").toAbsolutePath().normalize();

    private final QuoteSessionRepository sessionRepo;
    private final QuoteLineItemRepository lineItemRepo;
    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;
    private final PrinterMachineRepository machineRepo;
    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final OrcaProfileResolver orcaProfileResolver;
    private final com.printcalculator.repository.PricingPolicyRepository pricingRepo;
    private final com.printcalculator.service.ClamAVService clamAVService;

    public QuoteSessionController(QuoteSessionRepository sessionRepo,
                                  QuoteLineItemRepository lineItemRepo,
                                  SlicerService slicerService,
                                  QuoteCalculator quoteCalculator,
                                  PrinterMachineRepository machineRepo,
                                  FilamentMaterialTypeRepository materialRepo,
                                  FilamentVariantRepository variantRepo,
                                  OrcaProfileResolver orcaProfileResolver,
                                  com.printcalculator.repository.PricingPolicyRepository pricingRepo,
                                  com.printcalculator.service.ClamAVService clamAVService) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.orcaProfileResolver = orcaProfileResolver;
        this.pricingRepo = pricingRepo;
        this.clamAVService = clamAVService;
    }

    // 1. Start a new empty session
    @PostMapping(value = "")
    @Transactional
    public ResponseEntity<QuoteSession> createSession() {
        QuoteSession session = new QuoteSession();
        session.setStatus("ACTIVE");
        session.setPricingVersion("v1");
        // Default material/settings will be set when items are added or updated?
        // For now set safe defaults
        session.setMaterialCode("PLA"); 
        session.setSupportsEnabled(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plusDays(30)); 
        
        var policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        session.setSetupCostChf(quoteCalculator.calculateSessionSetupFee(policy));
        
        session = sessionRepo.save(session);
        return ResponseEntity.ok(session);
    }
    
    // 2. Add item to existing session
    @PostMapping(value = "/{id}/line-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<QuoteLineItem> addItemToExistingSession(
            @PathVariable UUID id,
            @RequestPart("settings") com.printcalculator.dto.PrintSettingsDto settings,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        QuoteLineItem item = addItemToSession(session, file, settings);
        return ResponseEntity.ok(item);
    }

    // Helper to add item
    private QuoteLineItem addItemToSession(QuoteSession session, MultipartFile file, com.printcalculator.dto.PrintSettingsDto settings) throws IOException {
        if (file.isEmpty()) throw new IOException("File is empty");

        // Scan for virus
        clamAVService.scan(file.getInputStream());

        // 1. Define Persistent Storage Path
        // Structure: storage_quotes/{sessionId}/{uuid}.{ext}
        Path sessionStorageDir = QUOTE_STORAGE_ROOT.resolve(session.getId().toString()).normalize();
        if (!sessionStorageDir.startsWith(QUOTE_STORAGE_ROOT)) {
            throw new IOException("Invalid quote session storage path");
        }
        Files.createDirectories(sessionStorageDir);

        String originalFilename = file.getOriginalFilename();
        String ext = getSafeExtension(originalFilename, "stl");
        String storedFilename = UUID.randomUUID() + "." + ext;
        Path persistentPath = sessionStorageDir.resolve(storedFilename).normalize();
        if (!persistentPath.startsWith(sessionStorageDir)) {
            throw new IOException("Invalid quote line-item storage path");
        }

        // Save file
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, persistentPath, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            // Apply Basic/Advanced Logic
            applyPrintSettings(settings);

            BigDecimal nozzleDiameter = BigDecimal.valueOf(settings.getNozzleDiameter() != null ? settings.getNozzleDiameter() : 0.4);

            // Pick machine (selected machine if provided, otherwise first active)
            PrinterMachine machine = resolvePrinterMachine(settings.getPrinterMachineId());

            // Resolve selected filament variant
            FilamentVariant selectedVariant = resolveFilamentVariant(settings);

            // Update session global settings from the most recent item added
            session.setMaterialCode(selectedVariant.getFilamentMaterialType().getMaterialCode());
            session.setNozzleDiameterMm(nozzleDiameter);
            session.setLayerHeightMm(BigDecimal.valueOf(settings.getLayerHeight() != null ? settings.getLayerHeight() : 0.2));
            session.setInfillPattern(settings.getInfillPattern());
            session.setInfillPercent(settings.getInfillDensity() != null ? settings.getInfillDensity().intValue() : 20);
            session.setSupportsEnabled(settings.getSupportsEnabled() != null ? settings.getSupportsEnabled() : false);
            sessionRepo.save(session);

            OrcaProfileResolver.ResolvedProfiles profiles = orcaProfileResolver.resolve(machine, nozzleDiameter, selectedVariant);
            String machineProfile = profiles.machineProfileName();
            String filamentProfile = profiles.filamentProfileName();

            String processProfile = "standard";
            if (settings.getLayerHeight() != null) {
                 if (settings.getLayerHeight() >= 0.28) processProfile = "draft";
                 else if (settings.getLayerHeight() <= 0.12) processProfile = "extra_fine";
            }
            
            // Build overrides map from settings
            Map<String, String> processOverrides = new HashMap<>();
            if (settings.getLayerHeight() != null) processOverrides.put("layer_height", String.valueOf(settings.getLayerHeight()));
            if (settings.getInfillDensity() != null) processOverrides.put("sparse_infill_density", settings.getInfillDensity() + "%");
            if (settings.getInfillPattern() != null) processOverrides.put("sparse_infill_pattern", settings.getInfillPattern());
            
            // 3. Slice (Use persistent path)
            PrintStats stats = slicerService.slice(
                persistentPath.toFile(), 
                machineProfile, 
                filamentProfile, 
                processProfile, 
                null, // machine overrides
                processOverrides
            );

            Optional<ModelDimensions> modelDimensions = slicerService.inspectModelDimensions(persistentPath.toFile());
            
            // 4. Calculate Quote
            QuoteResult result = quoteCalculator.calculate(stats, machine.getPrinterDisplayName(), selectedVariant);

            // 5. Create Line Item
            QuoteLineItem item = new QuoteLineItem();
            item.setQuoteSession(session);
            item.setOriginalFilename(file.getOriginalFilename());
            item.setStoredPath(QUOTE_STORAGE_ROOT.relativize(persistentPath).toString()); // SAVE PATH (relative to root)
            item.setQuantity(1);
            item.setColorCode(selectedVariant.getColorName());
            item.setFilamentVariant(selectedVariant);
            item.setStatus("READY"); // or CALCULATED
            
            item.setPrintTimeSeconds((int) stats.printTimeSeconds());
            item.setMaterialGrams(BigDecimal.valueOf(stats.filamentWeightGrams()));
            item.setUnitPriceChf(BigDecimal.valueOf(result.getTotalPrice()));
            
            // Store breakdown
            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("machine_cost", result.getTotalPrice()); // Excludes setup fee which is at session level
            breakdown.put("setup_fee", 0);
            item.setPricingBreakdown(breakdown);
            
            // Dimensions for shipping/package checks are computed server-side from the uploaded model.
            item.setBoundingBoxXMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.xMm()))
                    .orElseGet(() -> settings.getBoundingBoxX() != null ? BigDecimal.valueOf(settings.getBoundingBoxX()) : BigDecimal.ZERO));
            item.setBoundingBoxYMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.yMm()))
                    .orElseGet(() -> settings.getBoundingBoxY() != null ? BigDecimal.valueOf(settings.getBoundingBoxY()) : BigDecimal.ZERO));
            item.setBoundingBoxZMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.zMm()))
                    .orElseGet(() -> settings.getBoundingBoxZ() != null ? BigDecimal.valueOf(settings.getBoundingBoxZ()) : BigDecimal.ZERO));
            
            item.setCreatedAt(OffsetDateTime.now());
            item.setUpdatedAt(OffsetDateTime.now());
            
            return lineItemRepo.save(item);

        } catch (Exception e) {
            // Cleanup if failed
            Files.deleteIfExists(persistentPath);
            throw e;
        }
    }

    private void applyPrintSettings(com.printcalculator.dto.PrintSettingsDto settings) {
        if ("BASIC".equalsIgnoreCase(settings.getComplexityMode())) {
            // Set defaults based on Quality
            String quality = settings.getQuality() != null ? settings.getQuality().toLowerCase() : "standard";
            
            switch (quality) {
                case "draft":
                    settings.setLayerHeight(0.28);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                    break;
                case "high":
                    settings.setLayerHeight(0.12);
                    settings.setInfillDensity(20.0);
                    settings.setInfillPattern("gyroid");
                    break;
                case "standard":
                default:
                    settings.setLayerHeight(0.20);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                    break;
            }
        } else {
            // ADVANCED Mode: Use values from Frontend, set defaults if missing
            if (settings.getLayerHeight() == null) settings.setLayerHeight(0.20);
            if (settings.getInfillDensity() == null) settings.setInfillDensity(20.0);
            if (settings.getInfillPattern() == null) settings.setInfillPattern("grid");
        }
    }

    private PrinterMachine resolvePrinterMachine(Long printerMachineId) {
        if (printerMachineId != null) {
            PrinterMachine selected = machineRepo.findById(printerMachineId)
                    .orElseThrow(() -> new RuntimeException("Printer machine not found: " + printerMachineId));
            if (!Boolean.TRUE.equals(selected.getIsActive())) {
                throw new RuntimeException("Selected printer machine is not active");
            }
            return selected;
        }

        return machineRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active printer found"));
    }

    private FilamentVariant resolveFilamentVariant(com.printcalculator.dto.PrintSettingsDto settings) {
        if (settings.getFilamentVariantId() != null) {
            FilamentVariant variant = variantRepo.findById(settings.getFilamentVariantId())
                    .orElseThrow(() -> new RuntimeException("Filament variant not found: " + settings.getFilamentVariantId()));
            if (!Boolean.TRUE.equals(variant.getIsActive())) {
                throw new RuntimeException("Selected filament variant is not active");
            }
            return variant;
        }

        String requestedMaterialCode = normalizeRequestedMaterialCode(settings.getMaterial());

        FilamentMaterialType materialType = materialRepo.findByMaterialCode(requestedMaterialCode)
                .orElseGet(() -> materialRepo.findByMaterialCode("PLA")
                        .orElseThrow(() -> new RuntimeException("Fallback material PLA not configured")));

        String requestedColor = settings.getColor() != null ? settings.getColor().trim() : null;
        if (requestedColor != null && !requestedColor.isBlank()) {
            Optional<FilamentVariant> byColor = variantRepo.findByFilamentMaterialTypeAndColorName(materialType, requestedColor);
            if (byColor.isPresent() && Boolean.TRUE.equals(byColor.get().getIsActive())) {
                return byColor.get();
            }
        }

        return variantRepo.findFirstByFilamentMaterialTypeAndIsActiveTrue(materialType)
                .orElseThrow(() -> new RuntimeException("No active variant for material: " + requestedMaterialCode));
    }

    private String normalizeRequestedMaterialCode(String value) {
        if (value == null || value.isBlank()) {
            return "PLA";
        }

        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    // 3. Update Line Item
    @PatchMapping("/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<QuoteLineItem> updateLineItem(
            @PathVariable UUID lineItemId,
            @RequestBody Map<String, Object> updates
    ) {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        if (updates.containsKey("quantity")) {
            item.setQuantity((Integer) updates.get("quantity"));
        }
        if (updates.containsKey("color_code")) {
            item.setColorCode((String) updates.get("color_code"));
        }
        
        // Recalculate price if needed? 
        // For now, unit price is fixed in mock. Total is calculated on GET.
        
        item.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(lineItemRepo.save(item));
    }
    
    // 4. Delete Line Item
    @DeleteMapping("/{sessionId}/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<Void> deleteLineItem(
            @PathVariable UUID sessionId,
            @PathVariable UUID lineItemId
    ) {
        // Verify item belongs to session?
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }
        
        lineItemRepo.delete(item);
        return ResponseEntity.noContent().build();
    }
    
    // 5. Get Session (Session + Items + Total)
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getQuoteSession(@PathVariable UUID id) {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        List<QuoteLineItem> items = lineItemRepo.findByQuoteSessionId(id);
        
        // Calculate Totals and global session hours
        BigDecimal itemsTotal = BigDecimal.ZERO;
        BigDecimal totalSeconds = BigDecimal.ZERO;

        for (QuoteLineItem item : items) {
            BigDecimal lineTotal = item.getUnitPriceChf().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(lineTotal);
            
            if (item.getPrintTimeSeconds() != null) {
                totalSeconds = totalSeconds.add(BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        
        BigDecimal totalHours = totalSeconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        com.printcalculator.entity.PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        BigDecimal globalMachineCost = quoteCalculator.calculateSessionMachineCost(policy, totalHours);
        
        itemsTotal = itemsTotal.add(globalMachineCost);
        
        // Map items to DTO to embed distributed machine cost
        List<Map<String, Object>> itemsDto = new ArrayList<>();
        for (QuoteLineItem item : items) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", item.getId());
            dto.put("originalFilename", item.getOriginalFilename());
            dto.put("quantity", item.getQuantity());
            dto.put("printTimeSeconds", item.getPrintTimeSeconds());
            dto.put("materialGrams", item.getMaterialGrams());
            dto.put("colorCode", item.getColorCode());
            dto.put("filamentVariantId", item.getFilamentVariant() != null ? item.getFilamentVariant().getId() : null);
            dto.put("status", item.getStatus());
            
            BigDecimal unitPrice = item.getUnitPriceChf();
            if (totalSeconds.compareTo(BigDecimal.ZERO) > 0 && item.getPrintTimeSeconds() != null) {
                BigDecimal itemSeconds = BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal share = itemSeconds.divide(totalSeconds, 8, RoundingMode.HALF_UP);
                BigDecimal itemMachineCost = globalMachineCost.multiply(share);
                BigDecimal unitMachineCost = itemMachineCost.divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
                unitPrice = unitPrice.add(unitMachineCost);
            }
            dto.put("unitPriceChf", unitPrice);
            itemsDto.add(dto);
        }
        
        BigDecimal setupFee = session.getSetupCostChf() != null ? session.getSetupCostChf() : BigDecimal.ZERO;
        
        // Calculate shipping cost based on dimensions
        boolean exceedsBaseSize = false;
        for (QuoteLineItem item : items) {
            BigDecimal x = item.getBoundingBoxXMm() != null ? item.getBoundingBoxXMm() : BigDecimal.ZERO;
            BigDecimal y = item.getBoundingBoxYMm() != null ? item.getBoundingBoxYMm() : BigDecimal.ZERO;
            BigDecimal z = item.getBoundingBoxZMm() != null ? item.getBoundingBoxZMm() : BigDecimal.ZERO;
            
            BigDecimal[] dims = {x, y, z};
            java.util.Arrays.sort(dims);
            
            if (dims[2].compareTo(BigDecimal.valueOf(250.0)) > 0 ||
                dims[1].compareTo(BigDecimal.valueOf(176.0)) > 0 ||
                dims[0].compareTo(BigDecimal.valueOf(20.0)) > 0) {
                exceedsBaseSize = true;
                break;
            }
        }
        int totalQuantity = items.stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1)
                .sum();

        BigDecimal shippingCostChf;
        if (exceedsBaseSize) {
            shippingCostChf = totalQuantity > 5 ? BigDecimal.valueOf(9.00) : BigDecimal.valueOf(4.00);
        } else {
            shippingCostChf = BigDecimal.valueOf(2.00);
        }

        BigDecimal grandTotal = itemsTotal.add(setupFee).add(shippingCostChf);
        
        Map<String, Object> response = new HashMap<>();
        response.put("session", session);
        response.put("items", itemsDto);
        response.put("itemsTotalChf", itemsTotal); // Includes the base cost of all items + the global tiered machine cost
        response.put("shippingCostChf", shippingCostChf);
        response.put("globalMachineCostChf", globalMachineCost); // Provide it so frontend knows how much it was (optional now)
        response.put("grandTotalChf", grandTotal);
        
        return ResponseEntity.ok(response);
    }

    // 6. Download Line Item Content
    @GetMapping(value = "/{sessionId}/line-items/{lineItemId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> downloadLineItemContent(
            @PathVariable UUID sessionId,
            @PathVariable UUID lineItemId
    ) throws IOException {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        if (item.getStoredPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path path = resolveStoredQuotePath(item.getStoredPath(), sessionId);
        if (path == null || !Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        org.springframework.core.io.Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + item.getOriginalFilename() + "\"")
                .body(resource);
    }

    private String getSafeExtension(String filename, String fallback) {
        if (filename == null) {
            return fallback;
        }
        String cleaned = StringUtils.cleanPath(filename);
        if (cleaned.contains("..")) {
            return fallback;
        }
        int index = cleaned.lastIndexOf('.');
        if (index <= 0 || index >= cleaned.length() - 1) {
            return fallback;
        }
        String ext = cleaned.substring(index + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "stl" -> "stl";
            case "3mf" -> "3mf";
            case "step", "stp" -> "step";
            default -> fallback;
        };
    }

    private Path resolveStoredQuotePath(String storedPath, UUID expectedSessionId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedPath).normalize();
            Path resolved = raw.isAbsolute() ? raw : QUOTE_STORAGE_ROOT.resolve(raw).normalize();
            Path expectedSessionRoot = QUOTE_STORAGE_ROOT.resolve(expectedSessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }
}

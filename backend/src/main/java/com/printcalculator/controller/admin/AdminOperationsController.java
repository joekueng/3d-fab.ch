package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminContactRequestDto;
import com.printcalculator.dto.AdminFilamentStockDto;
import com.printcalculator.dto.AdminQuoteSessionDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.FilamentVariantStockKg;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.FilamentVariantStockKgRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminOperationsController {

    private final FilamentVariantStockKgRepository filamentStockRepo;
    private final FilamentVariantRepository filamentVariantRepo;
    private final CustomQuoteRequestRepository customQuoteRequestRepo;
    private final QuoteSessionRepository quoteSessionRepo;

    public AdminOperationsController(
            FilamentVariantStockKgRepository filamentStockRepo,
            FilamentVariantRepository filamentVariantRepo,
            CustomQuoteRequestRepository customQuoteRequestRepo,
            QuoteSessionRepository quoteSessionRepo
    ) {
        this.filamentStockRepo = filamentStockRepo;
        this.filamentVariantRepo = filamentVariantRepo;
        this.customQuoteRequestRepo = customQuoteRequestRepo;
        this.quoteSessionRepo = quoteSessionRepo;
    }

    @GetMapping("/filament-stock")
    public ResponseEntity<List<AdminFilamentStockDto>> getFilamentStock() {
        List<FilamentVariantStockKg> stocks = filamentStockRepo.findAll(Sort.by(Sort.Direction.ASC, "stockKg"));
        Set<Long> variantIds = stocks.stream()
                .map(FilamentVariantStockKg::getFilamentVariantId)
                .collect(Collectors.toSet());

        Map<Long, FilamentVariant> variantsById;
        if (variantIds.isEmpty()) {
            variantsById = Collections.emptyMap();
        } else {
            variantsById = filamentVariantRepo.findAllById(variantIds).stream()
                    .collect(Collectors.toMap(FilamentVariant::getId, variant -> variant));
        }

        List<AdminFilamentStockDto> response = stocks.stream().map(stock -> {
            FilamentVariant variant = variantsById.get(stock.getFilamentVariantId());
            AdminFilamentStockDto dto = new AdminFilamentStockDto();
            dto.setFilamentVariantId(stock.getFilamentVariantId());
            dto.setStockSpools(stock.getStockSpools());
            dto.setSpoolNetKg(stock.getSpoolNetKg());
            dto.setStockKg(stock.getStockKg());

            if (variant != null) {
                dto.setMaterialCode(
                        variant.getFilamentMaterialType() != null
                                ? variant.getFilamentMaterialType().getMaterialCode()
                                : "UNKNOWN"
                );
                dto.setVariantDisplayName(variant.getVariantDisplayName());
                dto.setColorName(variant.getColorName());
                dto.setActive(variant.getIsActive());
            } else {
                dto.setMaterialCode("UNKNOWN");
                dto.setVariantDisplayName("Variant " + stock.getFilamentVariantId());
                dto.setColorName("-");
                dto.setActive(false);
            }

            return dto;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/contact-requests")
    public ResponseEntity<List<AdminContactRequestDto>> getContactRequests() {
        List<AdminContactRequestDto> response = customQuoteRequestRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toContactRequestDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AdminQuoteSessionDto>> getQuoteSessions() {
        List<AdminQuoteSessionDto> response = quoteSessionRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toQuoteSessionDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    private AdminContactRequestDto toContactRequestDto(CustomQuoteRequest request) {
        AdminContactRequestDto dto = new AdminContactRequestDto();
        dto.setId(request.getId());
        dto.setRequestType(request.getRequestType());
        dto.setCustomerType(request.getCustomerType());
        dto.setEmail(request.getEmail());
        dto.setPhone(request.getPhone());
        dto.setName(request.getName());
        dto.setCompanyName(request.getCompanyName());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private AdminQuoteSessionDto toQuoteSessionDto(QuoteSession session) {
        AdminQuoteSessionDto dto = new AdminQuoteSessionDto();
        dto.setId(session.getId());
        dto.setStatus(session.getStatus());
        dto.setMaterialCode(session.getMaterialCode());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setExpiresAt(session.getExpiresAt());
        dto.setConvertedOrderId(session.getConvertedOrderId());
        return dto;
    }
}

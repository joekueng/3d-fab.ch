package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminFilamentMaterialTypeDto;
import com.printcalculator.dto.AdminFilamentVariantDto;
import com.printcalculator.dto.AdminUpsertFilamentMaterialTypeRequest;
import com.printcalculator.dto.AdminUpsertFilamentVariantRequest;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin/filaments")
@Transactional(readOnly = true)
public class AdminFilamentController {
    private static final BigDecimal MAX_NUMERIC_6_3 = new BigDecimal("999.999");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> ALLOWED_FINISH_TYPES = Set.of(
            "GLOSSY", "MATTE", "MARBLE", "SILK", "TRANSLUCENT", "SPECIAL"
    );

    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final OrderItemRepository orderItemRepo;

    public AdminFilamentController(
            FilamentMaterialTypeRepository materialRepo,
            FilamentVariantRepository variantRepo,
            QuoteLineItemRepository quoteLineItemRepo,
            OrderItemRepository orderItemRepo
    ) {
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.orderItemRepo = orderItemRepo;
    }

    @GetMapping("/materials")
    public ResponseEntity<List<AdminFilamentMaterialTypeDto>> getMaterials() {
        List<AdminFilamentMaterialTypeDto> response = materialRepo.findAll().stream()
                .sorted(Comparator.comparing(FilamentMaterialType::getMaterialCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toMaterialDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/variants")
    public ResponseEntity<List<AdminFilamentVariantDto>> getVariants() {
        List<AdminFilamentVariantDto> response = variantRepo.findAll().stream()
                .sorted(Comparator
                        .comparing((FilamentVariant v) -> {
                            FilamentMaterialType type = v.getFilamentMaterialType();
                            return type != null && type.getMaterialCode() != null ? type.getMaterialCode() : "";
                        }, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(v -> v.getVariantDisplayName() != null ? v.getVariantDisplayName() : "", String.CASE_INSENSITIVE_ORDER))
                .map(this::toVariantDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/materials")
    @Transactional
    public ResponseEntity<AdminFilamentMaterialTypeDto> createMaterial(
            @RequestBody AdminUpsertFilamentMaterialTypeRequest payload
    ) {
        String materialCode = normalizeAndValidateMaterialCode(payload);
        ensureMaterialCodeAvailable(materialCode, null);

        FilamentMaterialType material = new FilamentMaterialType();
        applyMaterialPayload(material, payload, materialCode);
        FilamentMaterialType saved = materialRepo.save(material);
        return ResponseEntity.ok(toMaterialDto(saved));
    }

    @PutMapping("/materials/{materialTypeId}")
    @Transactional
    public ResponseEntity<AdminFilamentMaterialTypeDto> updateMaterial(
            @PathVariable Long materialTypeId,
            @RequestBody AdminUpsertFilamentMaterialTypeRequest payload
    ) {
        FilamentMaterialType material = materialRepo.findById(materialTypeId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Filament material not found"));

        String materialCode = normalizeAndValidateMaterialCode(payload);
        ensureMaterialCodeAvailable(materialCode, materialTypeId);

        applyMaterialPayload(material, payload, materialCode);
        FilamentMaterialType saved = materialRepo.save(material);
        return ResponseEntity.ok(toMaterialDto(saved));
    }

    @PostMapping("/variants")
    @Transactional
    public ResponseEntity<AdminFilamentVariantDto> createVariant(
            @RequestBody AdminUpsertFilamentVariantRequest payload
    ) {
        FilamentMaterialType material = validateAndResolveMaterial(payload);
        String normalizedDisplayName = normalizeAndValidateVariantDisplayName(payload.getVariantDisplayName());
        String normalizedColorName = normalizeAndValidateColorName(payload.getColorName());
        validateNumericPayload(payload);
        ensureVariantDisplayNameAvailable(material, normalizedDisplayName, null);

        FilamentVariant variant = new FilamentVariant();
        variant.setCreatedAt(OffsetDateTime.now());
        applyVariantPayload(variant, payload, material, normalizedDisplayName, normalizedColorName);
        FilamentVariant saved = variantRepo.save(variant);
        return ResponseEntity.ok(toVariantDto(saved));
    }

    @PutMapping("/variants/{variantId}")
    @Transactional
    public ResponseEntity<AdminFilamentVariantDto> updateVariant(
            @PathVariable Long variantId,
            @RequestBody AdminUpsertFilamentVariantRequest payload
    ) {
        FilamentVariant variant = variantRepo.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Filament variant not found"));

        FilamentMaterialType material = validateAndResolveMaterial(payload);
        String normalizedDisplayName = normalizeAndValidateVariantDisplayName(payload.getVariantDisplayName());
        String normalizedColorName = normalizeAndValidateColorName(payload.getColorName());
        validateNumericPayload(payload);
        ensureVariantDisplayNameAvailable(material, normalizedDisplayName, variantId);

        applyVariantPayload(variant, payload, material, normalizedDisplayName, normalizedColorName);
        FilamentVariant saved = variantRepo.save(variant);
        return ResponseEntity.ok(toVariantDto(saved));
    }

    @DeleteMapping("/variants/{variantId}")
    @Transactional
    public ResponseEntity<Void> deleteVariant(@PathVariable Long variantId) {
        FilamentVariant variant = variantRepo.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Filament variant not found"));

        if (quoteLineItemRepo.existsByFilamentVariant_Id(variantId) || orderItemRepo.existsByFilamentVariant_Id(variantId)) {
            throw new ResponseStatusException(CONFLICT, "Variant is already used in quotes/orders and cannot be deleted");
        }

        variantRepo.delete(variant);
        return ResponseEntity.noContent().build();
    }

    private void applyMaterialPayload(
            FilamentMaterialType material,
            AdminUpsertFilamentMaterialTypeRequest payload,
            String normalizedMaterialCode
    ) {
        boolean isFlexible = payload != null && Boolean.TRUE.equals(payload.getIsFlexible());
        boolean isTechnical = payload != null && Boolean.TRUE.equals(payload.getIsTechnical());
        String technicalTypeLabel = payload != null && payload.getTechnicalTypeLabel() != null
                ? payload.getTechnicalTypeLabel().trim()
                : null;

        material.setMaterialCode(normalizedMaterialCode);
        material.setIsFlexible(isFlexible);
        material.setIsTechnical(isTechnical);
        material.setTechnicalTypeLabel(isTechnical && technicalTypeLabel != null && !technicalTypeLabel.isBlank()
                ? technicalTypeLabel
                : null);
    }

    private void applyVariantPayload(
            FilamentVariant variant,
            AdminUpsertFilamentVariantRequest payload,
            FilamentMaterialType material,
            String normalizedDisplayName,
            String normalizedColorName
    ) {
        String normalizedColorHex = normalizeAndValidateColorHex(payload.getColorHex());
        String normalizedFinishType = normalizeAndValidateFinishType(payload.getFinishType(), payload.getIsMatte());
        String normalizedBrand = normalizeOptional(payload.getBrand());

        variant.setFilamentMaterialType(material);
        variant.setVariantDisplayName(normalizedDisplayName);
        variant.setColorName(normalizedColorName);
        variant.setColorHex(normalizedColorHex);
        variant.setFinishType(normalizedFinishType);
        variant.setBrand(normalizedBrand);
        variant.setIsMatte(Boolean.TRUE.equals(payload.getIsMatte()) || "MATTE".equals(normalizedFinishType));
        variant.setIsSpecial(Boolean.TRUE.equals(payload.getIsSpecial()));
        variant.setCostChfPerKg(payload.getCostChfPerKg());
        variant.setStockSpools(payload.getStockSpools());
        variant.setSpoolNetKg(payload.getSpoolNetKg());
        variant.setIsActive(payload.getIsActive() == null || payload.getIsActive());
    }

    private String normalizeAndValidateMaterialCode(AdminUpsertFilamentMaterialTypeRequest payload) {
        if (payload == null || payload.getMaterialCode() == null || payload.getMaterialCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Material code is required");
        }
        return payload.getMaterialCode().trim().toUpperCase();
    }

    private String normalizeAndValidateVariantDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Variant display name is required");
        }
        return value.trim();
    }

    private String normalizeAndValidateColorName(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Color name is required");
        }
        return value.trim();
    }

    private String normalizeAndValidateColorHex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Color hex must be in format #RRGGBB");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeAndValidateFinishType(String finishType, Boolean isMatte) {
        String normalized = finishType == null || finishType.isBlank()
                ? (Boolean.TRUE.equals(isMatte) ? "MATTE" : "GLOSSY")
                : finishType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_FINISH_TYPES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid finish type");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private FilamentMaterialType validateAndResolveMaterial(AdminUpsertFilamentVariantRequest payload) {
        if (payload == null || payload.getMaterialTypeId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Material type id is required");
        }

        return materialRepo.findById(payload.getMaterialTypeId())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Material type not found"));
    }

    private void validateNumericPayload(AdminUpsertFilamentVariantRequest payload) {
        if (payload.getCostChfPerKg() == null || payload.getCostChfPerKg().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Cost CHF/kg must be >= 0");
        }
        validateNumeric63(payload.getStockSpools(), "Stock spools", true);
        validateNumeric63(payload.getSpoolNetKg(), "Spool net kg", false);
    }

    private void validateNumeric63(BigDecimal value, String fieldName, boolean allowZero) {
        if (value == null) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }

        if (allowZero) {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(BAD_REQUEST, fieldName + " must be >= 0");
            }
        } else if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must be > 0");
        }

        if (value.scale() > 3) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must have at most 3 decimal places");
        }

        if (value.compareTo(MAX_NUMERIC_6_3) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must be <= 999.999");
        }
    }

    private void ensureMaterialCodeAvailable(String materialCode, Long currentMaterialId) {
        materialRepo.findByMaterialCode(materialCode).ifPresent(existing -> {
            if (currentMaterialId == null || !existing.getId().equals(currentMaterialId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Material code already exists");
            }
        });
    }

    private void ensureVariantDisplayNameAvailable(FilamentMaterialType material, String displayName, Long currentVariantId) {
        variantRepo.findByFilamentMaterialTypeAndVariantDisplayName(material, displayName).ifPresent(existing -> {
            if (currentVariantId == null || !existing.getId().equals(currentVariantId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Variant display name already exists for this material");
            }
        });
    }

    private AdminFilamentMaterialTypeDto toMaterialDto(FilamentMaterialType material) {
        AdminFilamentMaterialTypeDto dto = new AdminFilamentMaterialTypeDto();
        dto.setId(material.getId());
        dto.setMaterialCode(material.getMaterialCode());
        dto.setIsFlexible(material.getIsFlexible());
        dto.setIsTechnical(material.getIsTechnical());
        dto.setTechnicalTypeLabel(material.getTechnicalTypeLabel());
        return dto;
    }

    private AdminFilamentVariantDto toVariantDto(FilamentVariant variant) {
        AdminFilamentVariantDto dto = new AdminFilamentVariantDto();
        dto.setId(variant.getId());

        FilamentMaterialType material = variant.getFilamentMaterialType();
        if (material != null) {
            dto.setMaterialTypeId(material.getId());
            dto.setMaterialCode(material.getMaterialCode());
            dto.setMaterialIsFlexible(material.getIsFlexible());
            dto.setMaterialIsTechnical(material.getIsTechnical());
            dto.setMaterialTechnicalTypeLabel(material.getTechnicalTypeLabel());
        }

        dto.setVariantDisplayName(variant.getVariantDisplayName());
        dto.setColorName(variant.getColorName());
        dto.setColorHex(variant.getColorHex());
        dto.setFinishType(variant.getFinishType());
        dto.setBrand(variant.getBrand());
        dto.setIsMatte(variant.getIsMatte());
        dto.setIsSpecial(variant.getIsSpecial());
        dto.setCostChfPerKg(variant.getCostChfPerKg());
        dto.setStockSpools(variant.getStockSpools());
        dto.setSpoolNetKg(variant.getSpoolNetKg());
        BigDecimal stockKg = BigDecimal.ZERO;
        if (variant.getStockSpools() != null && variant.getSpoolNetKg() != null) {
            stockKg = variant.getStockSpools().multiply(variant.getSpoolNetKg());
        }
        dto.setStockKg(stockKg);
        dto.setStockFilamentGrams(stockKg.multiply(BigDecimal.valueOf(1000)));
        dto.setIsActive(variant.getIsActive());
        dto.setCreatedAt(variant.getCreatedAt());
        return dto;
    }
}

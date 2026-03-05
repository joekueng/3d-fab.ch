package com.printcalculator.service.quote;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.NozzleLayerHeightPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Service
public class QuoteSessionSettingsService {
    private final PrinterMachineRepository machineRepo;
    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService;

    public QuoteSessionSettingsService(PrinterMachineRepository machineRepo,
                                       FilamentMaterialTypeRepository materialRepo,
                                       FilamentVariantRepository variantRepo,
                                       NozzleLayerHeightPolicyService nozzleLayerHeightPolicyService) {
        this.machineRepo = machineRepo;
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.nozzleLayerHeightPolicyService = nozzleLayerHeightPolicyService;
    }

    public void applyPrintSettings(PrintSettingsDto settings) {
        if (settings.getNozzleDiameter() == null) {
            settings.setNozzleDiameter(0.40);
        }

        if ("BASIC".equalsIgnoreCase(settings.getComplexityMode())) {
            String quality = settings.getQuality() != null ? settings.getQuality().toLowerCase() : "standard";

            switch (quality) {
                case "draft" -> {
                    settings.setLayerHeight(0.28);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                }
                case "extra_fine", "high_definition", "high" -> {
                    settings.setLayerHeight(0.12);
                    settings.setInfillDensity(20.0);
                    settings.setInfillPattern("gyroid");
                }
                case "standard" -> {
                    settings.setLayerHeight(0.20);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                }
                default -> {
                    settings.setLayerHeight(0.20);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                }
            }
        } else {
            if (settings.getInfillDensity() == null) {
                settings.setInfillDensity(20.0);
            }
            if (settings.getInfillPattern() == null) {
                settings.setInfillPattern("grid");
            }
        }
    }

    public void enforceCadPrintSettings(QuoteSession session, PrintSettingsDto settings) {
        settings.setComplexityMode("ADVANCED");
        settings.setMaterial(session.getMaterialCode() != null ? session.getMaterialCode() : "PLA");
        settings.setNozzleDiameter(session.getNozzleDiameterMm() != null ? session.getNozzleDiameterMm().doubleValue() : 0.4);
        settings.setLayerHeight(session.getLayerHeightMm() != null ? session.getLayerHeightMm().doubleValue() : 0.2);
        settings.setInfillPattern(session.getInfillPattern() != null ? session.getInfillPattern() : "grid");
        settings.setInfillDensity(session.getInfillPercent() != null ? session.getInfillPercent().doubleValue() : 20.0);
        settings.setSupportsEnabled(Boolean.TRUE.equals(session.getSupportsEnabled()));
    }

    public NozzleLayerSettings resolveNozzleAndLayer(PrintSettingsDto settings) {
        BigDecimal nozzleDiameter = nozzleLayerHeightPolicyService.resolveNozzle(
                settings.getNozzleDiameter() != null ? BigDecimal.valueOf(settings.getNozzleDiameter()) : null
        );
        BigDecimal layerHeight = nozzleLayerHeightPolicyService.resolveLayer(
                settings.getLayerHeight() != null ? BigDecimal.valueOf(settings.getLayerHeight()) : null,
                nozzleDiameter
        );
        if (!nozzleLayerHeightPolicyService.isAllowed(nozzleDiameter, layerHeight)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Layer height " + layerHeight.stripTrailingZeros().toPlainString()
                            + " is not allowed for nozzle " + nozzleDiameter.stripTrailingZeros().toPlainString()
                            + ". Allowed: " + nozzleLayerHeightPolicyService.allowedLayersLabel(nozzleDiameter)
            );
        }
        settings.setNozzleDiameter(nozzleDiameter.doubleValue());
        settings.setLayerHeight(layerHeight.doubleValue());
        return new NozzleLayerSettings(nozzleDiameter, layerHeight);
    }

    public PrinterMachine resolvePrinterMachine(Long printerMachineId) {
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

    public FilamentVariant resolveFilamentVariant(PrintSettingsDto settings) {
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

    public String normalizeRequestedMaterialCode(String value) {
        if (value == null || value.isBlank()) {
            return "PLA";
        }

        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    public String resolveQuality(PrintSettingsDto settings, BigDecimal layerHeight) {
        if (settings.getQuality() != null && !settings.getQuality().isBlank()) {
            return settings.getQuality().trim().toLowerCase(Locale.ROOT);
        }
        if (layerHeight == null) {
            return "standard";
        }
        if (layerHeight.compareTo(BigDecimal.valueOf(0.24)) >= 0) {
            return "draft";
        }
        if (layerHeight.compareTo(BigDecimal.valueOf(0.12)) <= 0) {
            return "extra_fine";
        }
        return "standard";
    }

    public record NozzleLayerSettings(BigDecimal nozzleDiameter, BigDecimal layerHeight) {
    }
}

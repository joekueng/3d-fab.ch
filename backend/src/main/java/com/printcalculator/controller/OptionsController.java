package com.printcalculator.controller;

import com.printcalculator.dto.OptionsResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.LayerHeightOption;
import com.printcalculator.entity.MaterialOrcaProfileMap;
import com.printcalculator.entity.NozzleOption;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.PrinterMachineProfile;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.LayerHeightOptionRepository;
import com.printcalculator.repository.MaterialOrcaProfileMapRepository;
import com.printcalculator.repository.NozzleOptionRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.OrcaProfileResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class OptionsController {

    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final LayerHeightOptionRepository layerHeightRepo;
    private final NozzleOptionRepository nozzleRepo;
    private final PrinterMachineRepository printerMachineRepo;
    private final MaterialOrcaProfileMapRepository materialOrcaMapRepo;
    private final OrcaProfileResolver orcaProfileResolver;

    public OptionsController(FilamentMaterialTypeRepository materialRepo,
                             FilamentVariantRepository variantRepo,
                             LayerHeightOptionRepository layerHeightRepo,
                             NozzleOptionRepository nozzleRepo,
                             PrinterMachineRepository printerMachineRepo,
                             MaterialOrcaProfileMapRepository materialOrcaMapRepo,
                             OrcaProfileResolver orcaProfileResolver) {
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.layerHeightRepo = layerHeightRepo;
        this.nozzleRepo = nozzleRepo;
        this.printerMachineRepo = printerMachineRepo;
        this.materialOrcaMapRepo = materialOrcaMapRepo;
        this.orcaProfileResolver = orcaProfileResolver;
    }

    @GetMapping("/api/calculator/options")
    @Transactional(readOnly = true)
    public ResponseEntity<OptionsResponse> getOptions(
            @RequestParam(value = "printerMachineId", required = false) Long printerMachineId,
            @RequestParam(value = "nozzleDiameter", required = false) Double nozzleDiameter
    ) {
        List<FilamentMaterialType> types = materialRepo.findAll();
        List<FilamentVariant> allVariants = variantRepo.findByIsActiveTrue().stream()
                .sorted(Comparator
                        .comparing((FilamentVariant v) -> safeMaterialCode(v.getFilamentMaterialType()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(v -> safeString(v.getVariantDisplayName()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        Set<Long> compatibleMaterialTypeIds = resolveCompatibleMaterialTypeIds(printerMachineId, nozzleDiameter);

        List<OptionsResponse.MaterialOption> materialOptions = types.stream()
                .sorted(Comparator.comparing(t -> safeString(t.getMaterialCode()), String.CASE_INSENSITIVE_ORDER))
                .map(type -> {
                    if (!compatibleMaterialTypeIds.isEmpty() && !compatibleMaterialTypeIds.contains(type.getId())) {
                        return null;
                    }

                    List<OptionsResponse.VariantOption> variants = allVariants.stream()
                            .filter(v -> v.getFilamentMaterialType() != null
                                    && v.getFilamentMaterialType().getId().equals(type.getId()))
                            .map(v -> new OptionsResponse.VariantOption(
                                    v.getId(),
                                    v.getVariantDisplayName(),
                                    v.getColorName(),
                                    resolveHexColor(v),
                                    v.getFinishType() != null ? v.getFinishType() : "GLOSSY",
                                    v.getStockSpools() != null ? v.getStockSpools().doubleValue() : 0d,
                                    toStockFilamentGrams(v),
                                    v.getStockSpools() == null || v.getStockSpools().doubleValue() <= 0
                            ))
                            .collect(Collectors.toList());

                    if (variants.isEmpty()) {
                        return null;
                    }

                    return new OptionsResponse.MaterialOption(
                            type.getMaterialCode(),
                            type.getMaterialCode() + (Boolean.TRUE.equals(type.getIsFlexible()) ? " (Flexible)" : " (Standard)"),
                            variants
                    );
                })
                .filter(m -> m != null)
                .toList();

        List<OptionsResponse.QualityOption> qualities = List.of(
                new OptionsResponse.QualityOption("draft", "Draft"),
                new OptionsResponse.QualityOption("standard", "Standard"),
                new OptionsResponse.QualityOption("extra_fine", "High Definition")
        );

        List<OptionsResponse.InfillPatternOption> patterns = List.of(
                new OptionsResponse.InfillPatternOption("grid", "Grid"),
                new OptionsResponse.InfillPatternOption("gyroid", "Gyroid"),
                new OptionsResponse.InfillPatternOption("cubic", "Cubic")
        );

        List<OptionsResponse.LayerHeightOptionDTO> layers = layerHeightRepo.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsActive()))
                .sorted(Comparator.comparing(LayerHeightOption::getLayerHeightMm))
                .map(l -> new OptionsResponse.LayerHeightOptionDTO(
                        l.getLayerHeightMm().doubleValue(),
                        String.format("%.2f mm", l.getLayerHeightMm())
                ))
                .toList();

        List<OptionsResponse.NozzleOptionDTO> nozzles = nozzleRepo.findAll().stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsActive()))
                .sorted(Comparator.comparing(NozzleOption::getNozzleDiameterMm))
                .map(n -> new OptionsResponse.NozzleOptionDTO(
                        n.getNozzleDiameterMm().doubleValue(),
                        String.format("%.1f mm%s", n.getNozzleDiameterMm(),
                                n.getExtraNozzleChangeFeeChf().doubleValue() > 0
                                        ? String.format(" (+ %.2f CHF)", n.getExtraNozzleChangeFeeChf())
                                        : " (Standard)")
                ))
                .toList();

        return ResponseEntity.ok(new OptionsResponse(materialOptions, qualities, patterns, layers, nozzles));
    }

    private Set<Long> resolveCompatibleMaterialTypeIds(Long printerMachineId, Double nozzleDiameter) {
        PrinterMachine machine = null;
        if (printerMachineId != null) {
            machine = printerMachineRepo.findById(printerMachineId).orElse(null);
        }
        if (machine == null) {
            machine = printerMachineRepo.findFirstByIsActiveTrue().orElse(null);
        }
        if (machine == null) {
            return Set.of();
        }

        BigDecimal nozzle = nozzleDiameter != null
                ? BigDecimal.valueOf(nozzleDiameter)
                : BigDecimal.valueOf(0.40);

        PrinterMachineProfile machineProfile = orcaProfileResolver
                .resolveMachineProfile(machine, nozzle)
                .orElse(null);

        if (machineProfile == null) {
            return Set.of();
        }

        List<MaterialOrcaProfileMap> maps = materialOrcaMapRepo.findByPrinterMachineProfileAndIsActiveTrue(machineProfile);
        return maps.stream()
                .map(MaterialOrcaProfileMap::getFilamentMaterialType)
                .filter(m -> m != null && m.getId() != null)
                .map(FilamentMaterialType::getId)
                .collect(Collectors.toSet());
    }

    private String resolveHexColor(FilamentVariant variant) {
        if (variant.getColorHex() != null && !variant.getColorHex().isBlank()) {
            return variant.getColorHex();
        }
        return getColorHex(variant.getColorName());
    }

    private double toStockFilamentGrams(FilamentVariant variant) {
        if (variant.getStockSpools() == null || variant.getSpoolNetKg() == null) {
            return 0d;
        }
        return variant.getStockSpools()
                .multiply(variant.getSpoolNetKg())
                .multiply(BigDecimal.valueOf(1000))
                .doubleValue();
    }

    private String safeMaterialCode(FilamentMaterialType type) {
        if (type == null || type.getMaterialCode() == null) {
            return "";
        }
        return type.getMaterialCode();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    // Temporary helper for legacy values where color hex is not yet set in DB
    private String getColorHex(String colorName) {
        if (colorName == null) {
            return "#9e9e9e";
        }
        String lower = colorName.toLowerCase();
        if (lower.contains("black") || lower.contains("nero")) return "#1a1a1a";
        if (lower.contains("white") || lower.contains("bianco")) return "#f5f5f5";
        if (lower.contains("blue") || lower.contains("blu")) return "#1976d2";
        if (lower.contains("red") || lower.contains("rosso")) return "#d32f2f";
        if (lower.contains("green") || lower.contains("verde")) return "#388e3c";
        if (lower.contains("orange") || lower.contains("arancione")) return "#ffa726";
        if (lower.contains("grey") || lower.contains("gray") || lower.contains("grigio")) {
            if (lower.contains("dark") || lower.contains("scuro")) return "#424242";
            return "#bdbdbd";
        }
        if (lower.contains("purple") || lower.contains("viola")) return "#7b1fa2";
        if (lower.contains("yellow") || lower.contains("giallo")) return "#fbc02d";
        return "#9e9e9e";
    }
}

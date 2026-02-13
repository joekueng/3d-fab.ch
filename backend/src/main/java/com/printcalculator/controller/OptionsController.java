package com.printcalculator.controller;

import com.printcalculator.dto.OptionsResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.*; // This line replaces specific entity imports
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.LayerHeightOptionRepository;
import com.printcalculator.repository.NozzleOptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class OptionsController {

    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;
    private final LayerHeightOptionRepository layerHeightRepo;
    private final NozzleOptionRepository nozzleRepo;

    public OptionsController(FilamentMaterialTypeRepository materialRepo,
                             FilamentVariantRepository variantRepo,
                             LayerHeightOptionRepository layerHeightRepo,
                             NozzleOptionRepository nozzleRepo) {
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
        this.layerHeightRepo = layerHeightRepo;
        this.nozzleRepo = nozzleRepo;
    }

    @GetMapping("/api/calculator/options")
    public ResponseEntity<OptionsResponse> getOptions() {
        // 1. Materials & Variants
        List<FilamentMaterialType> types = materialRepo.findAll();
        List<FilamentVariant> allVariants = variantRepo.findAll();

        List<OptionsResponse.MaterialOption> materialOptions = types.stream()
                .map(type -> {
                    List<OptionsResponse.VariantOption> variants = allVariants.stream()
                            .filter(v -> v.getFilamentMaterialType().getId().equals(type.getId()) && v.getIsActive())
                            .map(v -> new OptionsResponse.VariantOption(
                                    v.getVariantDisplayName(),
                                    v.getColorName(),
                                    getColorHex(v.getColorName()), // Need helper or store hex in DB
                                    v.getStockSpools().doubleValue() <= 0
                            ))
                            .collect(Collectors.toList());

                    // Only include material if it has active variants
                    if (variants.isEmpty()) return null;

                    return new OptionsResponse.MaterialOption(
                            type.getMaterialCode(),
                            type.getMaterialCode() + (type.getIsFlexible() ? " (Flexible)" : " (Standard)"),
                            variants
                    );
                })
                .filter(m -> m != null)
                .collect(Collectors.toList());
        
        // Sort: PLA first, then PETG, then others alphabetically
        materialOptions.sort((a, b) -> {
            String codeA = a.code();
            String codeB = b.code();
            
            if (codeA.equals("pla_basic")) return -1;
            if (codeB.equals("pla_basic")) return 1;
            
            if (codeA.equals("petg_basic")) return -1;
            if (codeB.equals("petg_basic")) return 1;
            
            return codeA.compareTo(codeB);
        });

        // 2. Qualities (Static as per user request)
        List<OptionsResponse.QualityOption> qualities = List.of(
                new OptionsResponse.QualityOption("draft", "Draft"),
                new OptionsResponse.QualityOption("standard", "Standard"),
                new OptionsResponse.QualityOption("extra_fine", "High Definition")
        );

        // 3. Infill Patterns (Static as per user request)
        List<OptionsResponse.InfillPatternOption> patterns = List.of(
                new OptionsResponse.InfillPatternOption("grid", "Grid"),
                new OptionsResponse.InfillPatternOption("gyroid", "Gyroid"),
                new OptionsResponse.InfillPatternOption("cubic", "Cubic")
        );

        // 4. Layer Heights
        List<OptionsResponse.LayerHeightOptionDTO> layers = layerHeightRepo.findAll().stream()
                .filter(l -> l.getIsActive())
                .sorted(Comparator.comparing(LayerHeightOption::getLayerHeightMm))
                .map(l -> new OptionsResponse.LayerHeightOptionDTO(
                        l.getLayerHeightMm().doubleValue(),
                        String.format("%.2f mm", l.getLayerHeightMm())
                ))
                .collect(Collectors.toList());

        // 5. Nozzles
        List<OptionsResponse.NozzleOptionDTO> nozzles = nozzleRepo.findAll().stream()
                .filter(n -> n.getIsActive())
                .sorted(Comparator.comparing(NozzleOption::getNozzleDiameterMm))
                .map(n -> new OptionsResponse.NozzleOptionDTO(
                        n.getNozzleDiameterMm().doubleValue(),
                        String.format("%.1f mm%s", n.getNozzleDiameterMm(),
                                n.getExtraNozzleChangeFeeChf().doubleValue() > 0 
                                ? String.format(" (+ %.2f CHF)", n.getExtraNozzleChangeFeeChf()) 
                                : " (Standard)")
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new OptionsResponse(materialOptions, qualities, patterns, layers, nozzles));
    }

    // Temporary helper until we add hex to DB
    private String getColorHex(String colorName) {
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
        return "#9e9e9e"; // Default grey
    }
}

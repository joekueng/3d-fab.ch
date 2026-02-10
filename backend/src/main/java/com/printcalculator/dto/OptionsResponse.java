package com.printcalculator.dto;

import java.util.List;

public record OptionsResponse(
    List<MaterialOption> materials,
    List<QualityOption> qualities,
    List<InfillPatternOption> infillPatterns,
    List<LayerHeightOptionDTO> layerHeights,
    List<NozzleOptionDTO> nozzleDiameters
) {
    public record MaterialOption(String code, String label, List<VariantOption> variants) {}
    public record VariantOption(String name, String colorName, String hexColor, boolean isOutOfStock) {}
    public record QualityOption(String id, String label) {}
    public record InfillPatternOption(String id, String label) {}
    public record LayerHeightOptionDTO(double value, String label) {}
    public record NozzleOptionDTO(double value, String label) {}
}

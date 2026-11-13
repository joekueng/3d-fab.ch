package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class MaterialPrintCompatibilityService {
    private static final Set<BigDecimal> TECHNICAL_MATERIAL_BLOCKED_NOZZLES = Set.of(
            new BigDecimal("0.20"),
            new BigDecimal("0.80")
    );
    private static final Set<BigDecimal> TECHNICAL_MATERIAL_LAYER_HEIGHTS = Set.of(
            new BigDecimal("0.160"),
            new BigDecimal("0.200"),
            new BigDecimal("0.240")
    );

    public static boolean isTpu85a(FilamentVariant variant) {
        String code = variant != null && variant.getFilamentMaterialType() != null
                ? variant.getFilamentMaterialType().getMaterialCode() : null;
        return code != null && "TPU85A".equals(code.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s_-]", ""));
    }

    public void validate(FilamentVariant variant, BigDecimal nozzleDiameter, BigDecimal layerHeight) {
        FilamentMaterialType materialType = variant != null ? variant.getFilamentMaterialType() : null;
        if (materialType == null) {
            return;
        }

        if (isTpu85a(variant) && nozzleDiameter != null && nozzleDiameter.compareTo(new BigDecimal("0.60")) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "TPU 85A requires the configured 0.6 mm nozzle profile");
        }

        if ("PLA-CF".equalsIgnoreCase(materialType.getMaterialCode() != null
                ? materialType.getMaterialCode().trim() : "")
                && nozzleDiameter != null && new BigDecimal("0.20").compareTo(nozzleDiameter) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PLA-CF cannot be printed with a 0.2 mm nozzle"
            );
        }

        if (!Boolean.TRUE.equals(materialType.getIsTechnical())) {
            return;
        }

        if (nozzleDiameter != null && TECHNICAL_MATERIAL_BLOCKED_NOZZLES.stream()
                .anyMatch(blocked -> blocked.compareTo(nozzleDiameter) == 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Technical materials cannot be printed with 0.2 mm or 0.8 mm nozzles"
            );
        }

        if (layerHeight != null && TECHNICAL_MATERIAL_LAYER_HEIGHTS.stream()
                .noneMatch(allowed -> allowed.compareTo(layerHeight) == 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Technical materials require a layer height of 0.16, 0.20 or 0.24 mm"
            );
        }
    }
}

package com.printcalculator.dto;

import java.math.BigDecimal;

public record ShopProductModelDto(
        String url,
        String originalFilename,
        String mimeType,
        Long fileSizeBytes,
        BigDecimal boundingBoxXMm,
        BigDecimal boundingBoxYMm,
        BigDecimal boundingBoxZMm
) {
}

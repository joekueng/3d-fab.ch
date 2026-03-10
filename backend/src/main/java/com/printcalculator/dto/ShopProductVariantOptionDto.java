package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopProductVariantOptionDto(
        UUID id,
        String sku,
        String variantLabel,
        String colorName,
        String colorHex,
        BigDecimal priceChf,
        Boolean isDefault
) {
}

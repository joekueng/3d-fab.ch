package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopProductSummaryDto(
        UUID id,
        String slug,
        String name,
        String excerpt,
        Boolean isFeatured,
        Integer sortOrder,
        ShopCategoryRefDto category,
        BigDecimal priceFromChf,
        BigDecimal priceToChf,
        ShopProductVariantOptionDto defaultVariant,
        PublicMediaUsageDto primaryImage,
        ShopProductModelDto model3d
) {
}

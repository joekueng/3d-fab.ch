package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShopProductDetailDto(
        UUID id,
        String slug,
        String name,
        String excerpt,
        String description,
        String seoTitle,
        String seoDescription,
        String ogTitle,
        String ogDescription,
        Boolean indexable,
        Boolean isFeatured,
        Integer sortOrder,
        ShopCategoryRefDto category,
        List<ShopCategoryRefDto> breadcrumbs,
        BigDecimal priceFromChf,
        BigDecimal priceToChf,
        ShopProductVariantOptionDto defaultVariant,
        List<ShopProductVariantOptionDto> variants,
        PublicMediaUsageDto primaryImage,
        List<PublicMediaUsageDto> images,
        ShopProductModelDto model3d,
        String publicPath,
        Map<String, String> localizedPaths
) {
}

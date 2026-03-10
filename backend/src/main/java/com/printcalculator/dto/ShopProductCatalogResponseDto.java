package com.printcalculator.dto;

import java.util.List;

public record ShopProductCatalogResponseDto(
        String categorySlug,
        Boolean featuredOnly,
        ShopCategoryDetailDto category,
        List<ShopProductSummaryDto> products
) {
}

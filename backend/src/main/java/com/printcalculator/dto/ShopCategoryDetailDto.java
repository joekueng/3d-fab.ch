package com.printcalculator.dto;

import java.util.List;
import java.util.UUID;

public record ShopCategoryDetailDto(
        UUID id,
        String slug,
        String name,
        String description,
        String seoTitle,
        String seoDescription,
        String ogTitle,
        String ogDescription,
        Boolean indexable,
        Integer sortOrder,
        Integer productCount,
        List<ShopCategoryRefDto> breadcrumbs,
        PublicMediaUsageDto primaryImage,
        List<PublicMediaUsageDto> images,
        List<ShopCategoryTreeDto> children
) {
}

package com.printcalculator.dto;

import java.util.List;
import java.util.UUID;

public record ShopCategoryTreeDto(
        UUID id,
        UUID parentCategoryId,
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
        PublicMediaUsageDto primaryImage,
        List<ShopCategoryTreeDto> children
) {
}

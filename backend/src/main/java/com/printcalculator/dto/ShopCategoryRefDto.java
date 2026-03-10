package com.printcalculator.dto;

import java.util.UUID;

public record ShopCategoryRefDto(
        UUID id,
        String slug,
        String name
) {
}

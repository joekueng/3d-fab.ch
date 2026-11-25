package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AdminOrderSearchRequest(
        @NotNull @Valid AdminPageQuery pagination,
        @NotNull @Valid AdminOrderSearchQuery filters
) {
}

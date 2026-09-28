package com.printcalculator.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable service detail, also stored as the order's agreed price snapshot. */
public record ServiceLineDto(
        @NotBlank @Size(max = 2000) String description,
        @NotNull BillingType billingType,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999") @Digits(integer = 5, fraction = 2) BigDecimal quantity,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999") @Digits(integer = 6, fraction = 2) BigDecimal unitPriceChf
) {
    public enum BillingType { HOURLY, FIXED }

    public BigDecimal totalChf() {
        return quantity.multiply(unitPriceChf).setScale(2, RoundingMode.HALF_UP);
    }

    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getLineTotalChf() {
        return totalChf();
    }

    @AssertTrue(message = "Fixed-price services must have quantity 1")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFixedQuantityValid() {
        return billingType != BillingType.FIXED || quantity != null && quantity.compareTo(BigDecimal.ONE) == 0;
    }
}

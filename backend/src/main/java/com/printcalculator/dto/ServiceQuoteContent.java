package com.printcalculator.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Single estimate. Financial totals are always calculated on the server. */
public record ServiceQuoteContent(
    @NotBlank @Size(max=160) String title,
    @NotBlank @Size(max=20000) String description,
    @NotNull PricingMode pricingMode,
    @DecimalMin("0.01") @DecimalMax("99999") @Digits(integer=5, fraction=2) BigDecimal estimatedHours,
    @DecimalMin("0.00") @DecimalMax("999999") @Digits(integer=6, fraction=2) BigDecimal hourlyRateChf,
    @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer=10, fraction=2) BigDecimal lumpSumChf,
    @NotNull VariationType variationType,
    @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer=10, fraction=2) BigDecimal variationValue,
    @NotNull OffsetDateTime validUntil,
    @NotNull @Pattern(regexp="it|en|de|fr") String language,
    // Legacy JSON field: ignored when reading older drafts and never exposed or enforced.
    @com.fasterxml.jackson.annotation.JsonIgnore String recipientEmail
) {
    public enum PricingMode { HOURLY_ESTIMATE, LUMP_SUM_ESTIMATE }
    public enum VariationType { PERCENTAGE, AMOUNT_CHF }
}

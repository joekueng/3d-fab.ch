package com.printcalculator.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Public, editorial data only. Never store session tokens or customer references here. */
public record PricingExampleDto(
        String material,
        String dimensionsMm,
        Integer quantity,
        BigDecimal priceChf,
        LocalDate quotedOn,
        Map<String, String> settings,
        Map<String, String> costsIncluded,
        boolean verified
) {
    private static final List<String> LANGUAGES = List.of("it", "en", "de", "fr");

    public boolean publishable() {
        return verified && material != null && !material.isBlank()
                && dimensionsMm != null && !dimensionsMm.isBlank()
                && quantity != null && quantity > 0
                && priceChf != null && priceChf.signum() > 0 && priceChf.scale() <= 2
                && quotedOn != null && !quotedOn.isAfter(LocalDate.now())
                && translated(settings) && translated(costsIncluded);
    }

    @JsonIgnore
    @AssertTrue(message = "Verified examples require material, dimensions, quantity, a positive CHF price, a past/current quote date and settings/cost inclusions in all four languages.")
    public boolean isValidForPublication() {
        return !verified || publishable();
    }

    private static boolean translated(Map<String, String> values) {
        return values != null && LANGUAGES.stream().allMatch(language ->
                values.get(language) != null && !values.get(language).isBlank());
    }
}

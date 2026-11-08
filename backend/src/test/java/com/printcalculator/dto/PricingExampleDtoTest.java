package com.printcalculator.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PricingExampleDtoTest {
    private PricingExampleDto example(BigDecimal amount, LocalDate date, Map<String, String> texts, boolean verified) {
        return new PricingExampleDto("PLA", "20 × 20 × 10", 1, amount, date, texts, texts, verified);
    }

    @Test
    void publicationRequiresRealisticDatedMoneyAndAllLocalesButDraftsCanBeIncomplete() {
        var texts = Map.of("it", "text", "en", "text", "de", "text", "fr", "text");
        assertTrue(example(new BigDecimal("12.50"), LocalDate.now(), texts, true).publishable());
        assertFalse(example(BigDecimal.ZERO, LocalDate.now(), texts, true).publishable());
        assertFalse(example(new BigDecimal("12.501"), LocalDate.now(), texts, true).publishable());
        assertFalse(example(BigDecimal.ONE, LocalDate.now().plusDays(1), texts, true).publishable());
        assertFalse(example(BigDecimal.ONE, LocalDate.now(), Map.of("it", "only"), true).publishable());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var draft = example(null, null, null, false);
            assertTrue(factory.getValidator().validate(draft).isEmpty());
            var payload = new AdminCreateMediaUsageRequest();
            payload.setPricingExample(example(null, null, null, true));
            assertFalse(factory.getValidator().validate(payload).isEmpty());
        }
    }
}

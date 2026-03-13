package com.printcalculator.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilamentVariantTest {

    @Test
    void getColorLabelForLanguageShouldReturnLocalizedValue() {
        FilamentVariant variant = new FilamentVariant();
        variant.setColorName("Orange");
        variant.setColorLabelIt("Arancione");
        variant.setColorLabelEn("Orange");
        variant.setColorLabelDe("Orange");
        variant.setColorLabelFr("Orange");

        assertEquals("Arancione", variant.getColorLabelForLanguage("it"));
        assertEquals("Orange", variant.getColorLabelForLanguage("en"));
        assertEquals("Orange", variant.getColorLabelForLanguage("de-CH"));
    }

    @Test
    void getColorLabelForLanguageShouldFallbackToColorName() {
        FilamentVariant variant = new FilamentVariant();
        variant.setColorName("Orange");

        assertEquals("Orange", variant.getColorLabelForLanguage("it"));
        assertEquals("Orange", variant.getColorLabelForLanguage("fr"));
    }
}

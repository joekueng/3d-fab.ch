package com.printcalculator.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopProductVariantTest {

    @Test
    void getColorLabelForLanguageShouldReturnLocalizedValue() {
        ShopProductVariant variant = new ShopProductVariant();
        variant.setColorName("Gray");
        variant.setColorLabelIt("Grigio");
        variant.setColorLabelEn("Gray");
        variant.setColorLabelDe("Grau");
        variant.setColorLabelFr("Gris");

        assertEquals("Grigio", variant.getColorLabelForLanguage("it"));
        assertEquals("Gray", variant.getColorLabelForLanguage("en"));
        assertEquals("Grau", variant.getColorLabelForLanguage("de"));
        assertEquals("Gris", variant.getColorLabelForLanguage("fr-CH"));
    }

    @Test
    void getColorLabelForLanguageShouldFallbackToColorName() {
        ShopProductVariant variant = new ShopProductVariant();
        variant.setColorName("Gray");

        assertEquals("Gray", variant.getColorLabelForLanguage("it"));
        assertEquals("Gray", variant.getColorLabelForLanguage("de"));
    }
}

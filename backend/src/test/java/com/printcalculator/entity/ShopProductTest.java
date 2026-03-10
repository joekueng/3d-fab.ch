package com.printcalculator.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopProductTest {

    @Test
    void localizedAccessorsShouldReturnLanguageSpecificValues() {
        ShopProduct product = new ShopProduct();
        product.setName("Desk Cable Clip");
        product.setNameIt("Fermacavo da scrivania");
        product.setNameEn("Desk Cable Clip");
        product.setNameDe("Schreibtisch-Kabelclip");
        product.setNameFr("Clip de cable de bureau");
        product.setExcerpt("Legacy excerpt");
        product.setExcerptIt("Clip compatta per i cavi sulla scrivania.");
        product.setExcerptEn("Compact clip to keep desk cables in place.");
        product.setExcerptDe("Kompakter Clip fur ordentliche Kabel auf dem Schreibtisch.");
        product.setExcerptFr("Clip compact pour garder les cables du bureau en ordre.");
        product.setDescription("Legacy description");
        product.setDescriptionIt("Supporto con base stabile e passaggio cavi frontale.");
        product.setDescriptionEn("Stable desk clip with front cable routing.");
        product.setDescriptionDe("Stabiler Tischclip mit frontaler Kabelfuhrung.");
        product.setDescriptionFr("Clip de bureau stable avec passage frontal des cables.");

        assertEquals("Fermacavo da scrivania", product.getNameForLanguage("it"));
        assertEquals("Desk Cable Clip", product.getNameForLanguage("en"));
        assertEquals("Schreibtisch-Kabelclip", product.getNameForLanguage("de"));
        assertEquals("Clip de cable de bureau", product.getNameForLanguage("fr"));
        assertEquals("Compact clip to keep desk cables in place.", product.getExcerptForLanguage("en"));
        assertEquals("Clip compact pour garder les cables du bureau en ordre.", product.getExcerptForLanguage("fr"));
        assertEquals("Stabiler Tischclip mit frontaler Kabelfuhrung.", product.getDescriptionForLanguage("de"));
    }

    @Test
    void localizedAccessorsShouldFallbackToLegacyValues() {
        ShopProduct product = new ShopProduct();
        product.setName("Desk Cable Clip");
        product.setExcerpt("Compact desk cable clip.");
        product.setDescription("Stable clip with front cable channel.");

        assertEquals("Desk Cable Clip", product.getNameForLanguage("it"));
        assertEquals("Compact desk cable clip.", product.getExcerptForLanguage("de"));
        assertEquals("Stable clip with front cable channel.", product.getDescriptionForLanguage("fr-CH"));
    }
}

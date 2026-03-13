package com.printcalculator.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopCategoryTest {

    @Test
    void localizedAccessorsShouldReturnLanguageSpecificValues() {
        ShopCategory category = new ShopCategory();
        category.setName("Desk accessories");
        category.setNameIt("Accessori da scrivania");
        category.setNameEn("Desk accessories");
        category.setNameDe("Schreibtischzubehor");
        category.setNameFr("Accessoires de bureau");
        category.setDescription("Legacy description");
        category.setDescriptionIt("Organizer e accessori stampati per la scrivania.");
        category.setDescriptionEn("Printed desk organizers and accessories.");
        category.setDescriptionDe("Gedruckte Organizer und Zubehor fur den Schreibtisch.");
        category.setDescriptionFr("Accessoires et organiseurs imprimes pour le bureau.");
        category.setSeoTitle("Legacy SEO title");
        category.setSeoTitleIt("Accessori da scrivania stampati in 3D");
        category.setSeoTitleEn("3D printed desk accessories");
        category.setSeoTitleDe("3D-gedruckte Schreibtischaccessoires");
        category.setSeoTitleFr("Accessoires de bureau imprimes en 3D");
        category.setSeoDescription("Legacy SEO description");
        category.setSeoDescriptionIt("Accessori da scrivania personalizzati e funzionali.");
        category.setSeoDescriptionEn("Functional custom desk accessories.");
        category.setSeoDescriptionDe("Funktionale personalisierte Schreibtischaccessoires.");
        category.setSeoDescriptionFr("Accessoires de bureau fonctionnels et personnalises.");

        assertEquals("Accessori da scrivania", category.getNameForLanguage("it"));
        assertEquals("Desk accessories", category.getNameForLanguage("en"));
        assertEquals("Schreibtischzubehor", category.getNameForLanguage("de"));
        assertEquals("Accessoires de bureau", category.getNameForLanguage("fr"));
        assertEquals("Gedruckte Organizer und Zubehor fur den Schreibtisch.", category.getDescriptionForLanguage("de"));
        assertEquals("3D printed desk accessories", category.getSeoTitleForLanguage("en"));
        assertEquals("Accessoires de bureau fonctionnels et personnalises.", category.getSeoDescriptionForLanguage("fr"));
    }

    @Test
    void localizedAccessorsShouldFallbackToLegacyValues() {
        ShopCategory category = new ShopCategory();
        category.setName("Desk accessories");
        category.setDescription("Printed desk organizers and accessories.");
        category.setSeoTitle("3D printed desk accessories");
        category.setSeoDescription("Functional custom desk accessories.");

        assertEquals("Desk accessories", category.getNameForLanguage("it"));
        assertEquals("Printed desk organizers and accessories.", category.getDescriptionForLanguage("de"));
        assertEquals("3D printed desk accessories", category.getSeoTitleForLanguage("fr-CH"));
        assertEquals("Functional custom desk accessories.", category.getSeoDescriptionForLanguage("en-US"));
    }
}

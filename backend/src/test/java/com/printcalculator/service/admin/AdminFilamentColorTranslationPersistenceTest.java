package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminTranslateLocalizedTextResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:color-translations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminFilamentColorTranslationService.class)
class AdminFilamentColorTranslationPersistenceTest {
    @Autowired
    EntityManager entities;
    @Autowired
    AdminFilamentColorTranslationService service;
    @Autowired
    FilamentVariantRepository filaments;
    @Autowired
    ShopProductVariantRepository shop;
    @MockitoBean
    AdminLocalizedTextTranslationService translations;

    @Test
    void persistsTranslationsForBothPublicColorSources() {
        FilamentMaterialType material = new FilamentMaterialType();
        material.setMaterialCode("PLA");
        material.setIsFlexible(false);
        material.setIsTechnical(false);
        entities.persist(material);
        FilamentVariant filament = new FilamentVariant();
        filament.setFilamentMaterialType(material);
        filament.setVariantDisplayName("PLA Black");
        filament.setColorName("Black");
        filament.setColorLabelIt("Nero");
        filament.setColorLabelEn("Old English");
        filament.setColorHex("#111111");
        filament.setIsMatte(false);
        filament.setIsSpecial(false);
        filament.setCostChfPerKg(BigDecimal.TEN);
        filament.setStockSpools(BigDecimal.ONE);
        filament.setSpoolNetKg(BigDecimal.ONE);
        filament.setIsActive(true);
        filament.setCreatedAt(OffsetDateTime.now());
        entities.persist(filament);
        ShopCategory category = new ShopCategory();
        category.setSlug("accessori");
        category.setName("Accessori");
        entities.persist(category);
        ShopProduct product = new ShopProduct();
        product.setCategory(category);
        product.setSlug("supporto");
        product.setName("Supporto");
        entities.persist(product);
        ShopProductVariant variant = new ShopProductVariant();
        variant.setProduct(product);
        variant.setVariantLabel("PLA");
        variant.setInternalMaterialCode("PLA");
        variant.setColorName("Black");
        variant.setColorLabelIt("Nero");
        variant.setColorHex("#111111");
        entities.persist(variant);
        entities.flush();
        entities.clear();
        AdminTranslateLocalizedTextResponse response = new AdminTranslateLocalizedTextResponse();
        response.setFields(Map.of("color_0", Map.of("en", "Black", "de", "Schwarz", "fr", "Noir")));
        when(translations.translateLocalizedText(any())).thenAnswer(invocation -> {
            // Simulate stock changing after the translation service read its color snapshots.
            entities.createQuery("update FilamentVariant v set v.stockSpools = 2 where v.id = :id")
                    .setParameter("id", filament.getId()).executeUpdate();
            return response;
        });

        service.translateColors();
        entities.flush();
        entities.clear();

        FilamentVariant savedFilament = filaments.findById(filament.getId()).orElseThrow();
        ShopProductVariant savedShop = shop.findById(variant.getId()).orElseThrow();
        assertEquals("Black", savedFilament.getColorLabelForLanguage("en"));
        assertEquals("Schwarz", savedFilament.getColorLabelForLanguage("de"));
        assertEquals("Noir", savedShop.getColorLabelForLanguage("fr"));
        assertEquals("Nero", savedShop.getColorLabelForLanguage("it"));
        assertEquals(new BigDecimal("2.000"), savedFilament.getStockSpools());
    }
}

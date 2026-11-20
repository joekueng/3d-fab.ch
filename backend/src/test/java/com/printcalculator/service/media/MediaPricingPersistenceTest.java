package com.printcalculator.service.media;

import com.printcalculator.dto.PricingExampleDto;
import com.printcalculator.entity.MediaAsset;
import com.printcalculator.entity.MediaUsage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:media-pricing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "logging.level.org.hibernate.SQL=OFF"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.context.annotation.Import(MediaPricingPersistenceTest.H2JsonSyntax.class)
class MediaPricingPersistenceTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class H2JsonSyntax {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer h2Json() {
            // Keep PostgreSQL text DDL while H2 needs FORMAT JSON for object values.
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> sql.replace("cast(? as jsonb)", "? format json"));
        }
    }

    @Autowired EntityManager entityManager;

    @Test
    void optionalPricingJsonRoundTripsAlongsideLegacyMedia() {
        var asset = new MediaAsset();
        asset.setOriginalFilename("synthetic-sample.jpg");
        asset.setStorageKey("test/synthetic-sample.jpg");
        asset.setMimeType("image/jpeg");
        asset.setFileSizeBytes(12L);
        asset.setSha256Hex("synthetic-hash");
        asset.setStatus("READY");
        asset.setVisibility("PUBLIC");
        asset.setCreatedAt(OffsetDateTime.now());
        asset.setUpdatedAt(OffsetDateTime.now());
        entityManager.persist(asset);
        var usage = usage(asset, "PRICING_EXAMPLE", "functional");
        var texts = Map.of("it", "Stampa e IVA; spedizione esclusa", "en", "Print and VAT; shipping excluded", "de", "Druck und MWST; Versand ausgeschlossen", "fr", "Impression et TVA ; transport exclu");
        var details = new PricingExampleDto("PLA", "20 × 20 × 10", 1, new BigDecimal("12.50"), LocalDate.now(), texts, texts, true);
        usage.setPricingExample(details);
        entityManager.persist(usage);
        var legacy = usage(asset, "HOME_SECTION", "gallery");
        entityManager.persist(legacy);
        entityManager.flush();
        entityManager.clear();
        assertEquals(details, entityManager.find(MediaUsage.class, usage.getId()).getPricingExample());
        assertNull(entityManager.find(MediaUsage.class, legacy.getId()).getPricingExample());
    }

    private MediaUsage usage(MediaAsset asset, String type, String key) {
        var usage = new MediaUsage();
        usage.setMediaAsset(asset);
        usage.setUsageType(type);
        usage.setUsageKey(key);
        usage.setSortOrder(0);
        usage.setIsPrimary(true);
        usage.setIsActive(true);
        usage.setCreatedAt(OffsetDateTime.now());
        return usage;
    }
}

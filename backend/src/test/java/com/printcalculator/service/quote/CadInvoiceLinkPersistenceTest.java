package com.printcalculator.service.quote;

import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.SessionCleanupService;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CadInvoiceLinkPersistenceTest {
    @Test
    void retainsRedirectAndReactivatesReservedUuidAcrossDatabaseReloads() {
        try (var factory = new Configuration().addAnnotatedClass(QuoteSession.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:cadLinkPersistence;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory()) {
            UUID id;
            try (var database = factory.openSession()) {
                var tx = database.beginTransaction();
                var row = new QuoteSession();
                row.setStatus("CAD_DELETED");
                row.setSessionType("PRINT_QUOTE");
                row.setMaterialCode("PLA");
                row.setPricingVersion("v1");
                row.setSetupCostChf(BigDecimal.ZERO);
                row.setExpiresAt(OffsetDateTime.now().minusMonths(1));
                row.setServiceLines(List.of());
                row.setDeletedRedirectPath("/contact");
                database.persist(row);
                tx.commit();
                id = row.getId();
            }
            try (var database = factory.openSession()) {
                var tx = database.beginTransaction();
                var row = database.find(QuoteSession.class, id);
                assertEquals("CAD_DELETED", row.getStatus());
                assertEquals("/contact", row.getDeletedRedirectPath());
                assertTrue(row.getServiceLines().isEmpty());
                row.setDeletedRedirectPath(null);
                row.setStatus("CAD_ACTIVE");
                row.setInvoiceName("Replacement");
                tx.commit();
            }
            try (var database = factory.openSession()) {
                var row = database.find(QuoteSession.class, id);
                assertEquals(id, row.getId());
                assertEquals("CAD_ACTIVE", row.getStatus());
                assertNull(row.getDeletedRedirectPath());
                assertEquals("Replacement", row.getInvoiceName());
            }
        }
    }

    @Test
    void expiryCleanupPreservesDeletedInvoiceLinkReservations() {
        var sessions = mock(QuoteSessionRepository.class);
        var row = new QuoteSession();
        row.setId(UUID.randomUUID());
        row.setStatus("CAD_DELETED");
        row.setExpiresAt(OffsetDateTime.now().minusMonths(1));
        when(sessions.findByExpiresAtBefore(any())).thenReturn(List.of(row));
        new SessionCleanupService(sessions).cleanupOldSessions();
        verify(sessions, never()).delete(any());
    }
}

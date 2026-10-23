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
    void calculatorAssociationAndDetachmentSurviveReloadWithoutLosingTheOriginalSession() {
        try (var factory = new Configuration().addAnnotatedClass(QuoteSession.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:calculatorAssociation;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory()) {
            UUID invoiceId; UUID sourceId;
            try (var db = factory.openSession()) {
                var tx = db.beginTransaction();
                var source = persistentSession("ACTIVE"); db.persist(source);
                var invoice = persistentSession("CAD_ACTIVE"); invoice.setCalculatorSession(source); db.persist(invoice);
                tx.commit(); invoiceId = invoice.getId(); sourceId = source.getId();
            }
            try (var db = factory.openSession()) {
                var tx = db.beginTransaction(); var invoice = db.find(QuoteSession.class, invoiceId);
                assertEquals(sourceId, invoice.getPrintSource().getId());
                invoice.setCalculatorSession(null); invoice.setCalculatorDetached(true); tx.commit();
            }
            try (var db = factory.openSession()) {
                var tx = db.beginTransaction(); var invoice = db.find(QuoteSession.class, invoiceId);
                assertNull(invoice.getPrintSource());
                var source = db.find(QuoteSession.class, sourceId); assertNotNull(source);
                assertEquals(new BigDecimal("5.00"), source.getSetupCostChf());
                invoice.setCalculatorSession(source); invoice.setCalculatorDetached(false); tx.commit();
            }
            try (var db = factory.openSession()) {
                assertEquals(sourceId, db.find(QuoteSession.class, invoiceId).getPrintSource().getId());
            }
        }
    }

    @Test
    void expiryCleanupKeepsCalculatorSessionsRetainedByAnInvoice() {
        var sessions = mock(QuoteSessionRepository.class);
        var source = persistentSession("ACTIVE"); source.setId(UUID.randomUUID());
        source.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(sessions.findByExpiresAtBefore(any())).thenReturn(List.of(source));
        when(sessions.existsByCalculatorSession_Id(source.getId())).thenReturn(true);
        new SessionCleanupService(sessions).cleanupOldSessions();
        verify(sessions, never()).delete(any());
    }

    private QuoteSession persistentSession(String status) {
        var session = new QuoteSession(); session.setStatus(status); session.setSessionType("PRINT_QUOTE");
        session.setMaterialCode("PLA"); session.setPricingVersion("v1"); session.setSetupCostChf(new BigDecimal("5"));
        session.setExpiresAt(OffsetDateTime.now().plusDays(30)); return session;
    }

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

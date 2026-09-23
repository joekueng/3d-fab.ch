package com.printcalculator.service.payment;

import com.printcalculator.entity.*;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.*;
import com.printcalculator.service.email.PaymentEmailOutbox;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.url=jdbc:h2:mem:payment-workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "app.payment.reported-email-delay=PT5M"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentService.class, PaymentEmailOutbox.class, PaymentWorkflowIntegrationTest.H2LockSyntax.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentWorkflowIntegrationTest {
    // Keep the repository's PostgreSQL text mappings; translate only its lock syntax for H2.
    @org.springframework.boot.test.context.TestConfiguration
    static class H2LockSyntax {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer h2Locks() {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> sql.replace("for no key update", "for update"));
        }
    }
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired PaymentEmailJobRepository jobs;
    @Autowired PaymentService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    UUID id;

    @BeforeEach void createOrder() {
        Order order = new Order();
        order.setSourceType("CALCULATOR"); order.setStatus("PENDING_PAYMENT");
        order.setCustomerEmail("fixture@example.test"); order.setBillingCustomerType("PRIVATE");
        order.setBillingAddressLine1("Teststrasse 1"); order.setBillingZip("8000");
        order.setBillingCity("Zurich"); order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(true); order.setCurrency("CHF");
        order.setSetupCostChf(BigDecimal.ZERO); order.setShippingCostChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO); order.setSubtotalChf(new BigDecimal("23.90"));
        order.setTotalChf(new BigDecimal("23.90")); order.setIsCadOrder(false); order.setCadTotalChf(BigDecimal.ZERO);
        id = orders.saveAndFlush(order).getId();
        service.getOrCreatePaymentForOrder(order, "OTHER");
    }
    @AfterEach void cleanup() {
        jobs.deleteAll(); payments.deleteAll(); orders.deleteAll();
    }
    @Test void queueAndPaymentCommitTogetherAndSurviveNewTransaction() {
        service.reportPayment(id, "TWINT");
        var first = jobs.findAll().getFirst();
        var reported = payments.findByOrder_Id(id).orElseThrow().getReportedAt();
        assertEquals(reported.plusMinutes(5).toInstant(), first.getDueAt().toInstant());
        service.reportPayment(id, "TWINT");
        assertEquals(1, jobs.count());
        assertEquals(first.getDueAt().toInstant(), jobs.findAll().getFirst().getDueAt().toInstant());
        service.confirmPayment(id, "BANK_TRANSFER");
        assertEquals("RECEIVED", payments.findByOrder_Id(id).orElseThrow().getStatus());
        assertEquals("PAID", orders.findById(id).orElseThrow().getStatus());
        assertEquals(1, jobs.findAll().stream().filter(j -> j.getKind().equals("CONFIRMED")).count());
        assertEquals("CANCELLED", jobs.findById(first.getId()).orElseThrow().getStatus());
    }
    @Test void rollbackDoesNotLeaveAQueuedConfirmation() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.confirmPayment(id, "TWINT");
            tx.setRollbackOnly();
        });
        assertEquals("PENDING", payments.findByOrder_Id(id).orElseThrow().getStatus());
        assertEquals(0, jobs.count());
    }
    @Test void concurrentReportAndConfirmHaveOneConfirmationAndNoRegression() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var report = executor.submit(() -> { start.await(); return service.reportPayment(id, "TWINT"); });
            var manual = executor.submit(() -> { start.await(); return service.confirmPayment(id, "BANK_TRANSFER"); });
            var automatic = executor.submit(() -> { start.await(); return service.confirmPayment(id, "TWINT"); });
            start.countDown();
            report.get(10, TimeUnit.SECONDS); manual.get(10, TimeUnit.SECONDS); automatic.get(10, TimeUnit.SECONDS);
        }
        assertEquals("RECEIVED", payments.findByOrder_Id(id).orElseThrow().getStatus());
        assertEquals("PAID", orders.findById(id).orElseThrow().getStatus());
        assertEquals(1, jobs.findAll().stream().filter(j -> j.getKind().equals("CONFIRMED")).count());
        assertTrue(jobs.findAll().stream().filter(j -> j.getKind().equals("REPORTED")).allMatch(j -> j.getStatus().equals("CANCELLED")));
    }
    @Test void lateConfirmationPreservesDatesMethodAndProduction() {
        service.confirmPayment(id, "BANK_TRANSFER");
        var original = payments.findByOrder_Id(id).orElseThrow();
        var paidAt = orders.findById(id).orElseThrow().getPaidAt();
        // Isolate the late-confirmation transition; avoid the PostgreSQL/H2 timestamp rewrite mismatch.
        jdbc.update("update orders set status = 'IN_PRODUCTION' where order_id = ?", id);
        service.confirmPayment(id, "TWINT");
        var current = payments.findByOrder_Id(id).orElseThrow();
        assertEquals(original.getReceivedAt().toInstant(), current.getReceivedAt().toInstant());
        assertEquals("BANK_TRANSFER", current.getMethod());
        assertEquals("IN_PRODUCTION", orders.findById(id).orElseThrow().getStatus());
        assertEquals(paidAt.toInstant(), orders.findById(id).orElseThrow().getPaidAt().toInstant());
        assertEquals(1, jobs.count());
    }
    @Test void persistedWindowExpiresAndOnlyFirstReportReactivatesIt() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);
        assertTrue(orders.hasActivePaymentWindow(cutoff));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> orders.findLockedById(id).orElseThrow().setCreatedAt(cutoff.minusDays(1)));
        assertFalse(orders.hasActivePaymentWindow(cutoff));
        service.reportPayment(id, "TWINT");
        assertTrue(orders.hasActivePaymentWindow(cutoff));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> payments.findByOrder_Id(id).orElseThrow().setReportedAt(cutoff.minusMinutes(1)));
        service.reportPayment(id, "TWINT");
        assertFalse(orders.hasActivePaymentWindow(cutoff));
        service.confirmPayment(id, "TWINT");
        assertFalse(orders.hasActivePaymentWindow(cutoff));
        assertEquals(java.util.List.of(id), orders.findIdsByUuidPrefix(id.toString().substring(0, 8)));
    }
}

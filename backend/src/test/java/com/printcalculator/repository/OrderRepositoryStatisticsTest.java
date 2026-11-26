package com.printcalculator.repository;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:order-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryStatisticsTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private QuoteSessionRepository sessionRepository;

    @Autowired
    private QuoteLineItemRepository lineItems;

    @Test
    void paidStatistics_includeReceivedAndLegacyCompletedPayments() {
        saveOrderWithPayment("received@example.test", "PAID", "RECEIVED", "10.00");
        saveOrderWithPayment("RECEIVED@example.test", "IN_PRODUCTION", "COMPLETED", "20.00");
        saveOrderWithPayment("reported@example.test", "PAID", "REPORTED", "30.00");
        saveOrderWithPayment("cancelled@example.test", "CANCELLED", "RECEIVED", "40.00");

        assertEquals(2L, orderRepository.countPaidNonCancelledForStatistics());
        assertEquals(new BigDecimal("30.00"), orderRepository.sumPaidNonCancelledTotalsForStatistics());
        assertEquals(15.0, orderRepository.averagePaidNonCancelledTotalsForStatistics());
        assertEquals(1L, orderRepository.countUniquePaidNonCancelledCustomersForStatistics());
    }

    @Test
    void paidStatistics_includeCompletedOrdersWithoutConfirmedPaymentAndCountEachOrderOnce() {
        saveOrderWithPayment("completed@example.test", "COMPLETED", null, "10.00");
        saveOrderWithPayment("COMPLETED@example.test", "COMPLETED", "PENDING", "20.00");
        saveOrderWithPayment("reported@example.test", "COMPLETED", "REPORTED", "30.00");
        saveOrderWithPayment("paid@example.test", "COMPLETED", "RECEIVED", "40.00");
        saveOrderWithPayment("cancelled@example.test", "CANCELLED", "RECEIVED", "100.00");
        saveOrderWithPayment("pending@example.test", "PENDING_PAYMENT", "PENDING", "200.00");

        assertEquals(4L, orderRepository.countPaidNonCancelledForStatistics());
        assertEquals(new BigDecimal("100.00"), orderRepository.sumPaidNonCancelledTotalsForStatistics());
        assertEquals(25.0, orderRepository.averagePaidNonCancelledTotalsForStatistics());
        assertEquals(3L, orderRepository.countUniquePaidNonCancelledCustomersForStatistics());
    }

    @Test
    void sessionStatistics_excludeDeletedSessionsAndApplyTheSamePaidRuleAsOrders() {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-24T12:00:00Z");
        QuoteSession completed = saveSession("CONVERTED", now.minusDays(1));
        QuoteSession paid = saveSession("CONVERTED", now.minusDays(1));
        QuoteSession cancelled = saveSession("CONVERTED", now.minusDays(1));
        QuoteSession abandoned = saveSession("ACTIVE", now.minusDays(1));
        QuoteSession active = saveSession("ACTIVE", now.plusDays(1));
        QuoteSession deleted = saveSession("CAD_DELETED", now.minusDays(1));
        saveSession("ACTIVE", now.minusDays(1));

        saveItem(completed, now, true);
        saveItem(completed, now, false);
        saveItem(paid, now, false);
        saveItem(cancelled, now, false);
        saveItem(abandoned, now, false);
        saveItem(active, now, false);
        saveItem(deleted, now, true);
        saveOrderWithPayment("completed@example.test", "COMPLETED", null, "10.00").setSourceQuoteSession(completed);
        saveOrderWithPayment("paid@example.test", "PAID", "RECEIVED", "10.00").setSourceQuoteSession(paid);
        saveOrderWithPayment("cancelled@example.test", "CANCELLED", "RECEIVED", "10.00").setSourceQuoteSession(cancelled);
        saveOrderWithPayment("deleted@example.test", "COMPLETED", null, "10.00").setSourceQuoteSession(deleted);
        orderRepository.flush();

        assertEquals(6L, sessionRepository.countAllForStatistics());
        assertEquals(5L, sessionRepository.countWithItemsForStatistics());
        assertEquals(6L, sessionRepository.countLineItemsForStatistics());
        assertEquals(3L, sessionRepository.countConvertedForStatistics());
        assertEquals(2L, sessionRepository.countPaidConvertedForStatistics());
        assertEquals(1L, sessionRepository.countModifiedForStatistics());
        assertEquals(2L, sessionRepository.countExpiredWithoutPaidConversionForStatistics(now));
    }

    private QuoteSession saveSession(String status, OffsetDateTime expiresAt) {
        QuoteSession session = new QuoteSession();
        session.setStatus(status);
        session.setExpiresAt(expiresAt);
        session.setPricingVersion("test");
        session.setMaterialCode("PLA");
        session.setSetupCostChf(BigDecimal.ZERO);
        return sessionRepository.save(session);
    }

    private void saveItem(QuoteSession session, OffsetDateTime createdAt, boolean modified) {
        QuoteLineItem item = new QuoteLineItem();
        item.setQuoteSession(session);
        item.setStatus("READY");
        item.setOriginalFilename("fixture.stl");
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(modified ? createdAt.plusMinutes(1) : createdAt);
        lineItems.save(item);
    }

    private Order saveOrderWithPayment(String email, String orderStatus, String paymentStatus, String totalChf) {
        BigDecimal total = new BigDecimal(totalChf);
        Order order = new Order();
        order.setSourceType("CALCULATOR");
        order.setStatus(orderStatus);
        order.setCustomerEmail(email);
        order.setBillingCustomerType("PRIVATE");
        order.setBillingAddressLine1("Teststrasse 1");
        order.setBillingZip("8000");
        order.setBillingCity("Zurich");
        order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(true);
        order.setCurrency("CHF");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSubtotalChf(total);
        order.setIsCadOrder(false);
        order.setCadTotalChf(BigDecimal.ZERO);
        order.setTotalChf(total);
        order = orderRepository.save(order);

        if (paymentStatus == null) {
            return order;
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod("OTHER");
        payment.setStatus(paymentStatus);
        payment.setCurrency("CHF");
        payment.setAmountChf(total);
        payment.setInitiatedAt(OffsetDateTime.now());
        paymentRepository.save(payment);
        return order;
    }
}

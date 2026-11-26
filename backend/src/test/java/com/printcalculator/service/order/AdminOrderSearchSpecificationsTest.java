package com.printcalculator.service.order;

import com.printcalculator.dto.AdminOrderSearchQuery;
import com.printcalculator.dto.AdminOrderSearchQuery.OrderStatus;
import com.printcalculator.dto.AdminOrderSearchQuery.OrderType;
import com.printcalculator.dto.AdminOrderSearchQuery.PaymentStatus;
import com.printcalculator.dto.AdminPageQuery;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:order-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminOrderSearchSpecificationsTest {
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-11-24T12:00:00Z");

    @Autowired
    private OrderRepository orders;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private OrderItemRepository items;

    @Test
    void searchFindsOlderCustomerOrdersBeforePaginationAndCombinesFilters() {
        Order mixed = saveOrder("Mario@example.test", "PAID", CREATED, false);
        Order pending = saveOrder("mario@example.test", "PENDING_PAYMENT", CREATED, false);
        Order cad = saveOrder("mario@example.test", "PAID", CREATED, true);
        for (Order order : List.of(mixed, pending, cad)) {
            order.setBillingFirstName("Mario");
            order.setBillingLastName("Rossi");
        }
        mixed.setBillingCompanyName("Studio 100%");
        cad.setBillingContactPerson("Referente Rossi");
        savePayment(mixed, "RECEIVED");
        savePayment(cad, "COMPLETED");
        saveItem(mixed, "SHOP_PRODUCT");
        saveItem(mixed, "PRINT_FILE");
        saveItem(mixed, "PRINT_FILE");
        saveItem(cad, "SHOP_PRODUCT");
        Order shop = saveOrder("shop@example.test", "PAID", CREATED, false);
        saveItem(shop, "SHOP_PRODUCT");

        for (int i = 0; i < 51; i++) {
            saveOrder("other@example.test", "PAID", CREATED.plusSeconds(1), false);
        }

        Page<Order> recent = find(null, null, null, null, 0, 25);
        assertEquals(55, recent.getTotalElements());
        assertFalse(recent.getContent().stream().anyMatch(order -> order.getId().equals(mixed.getId())));

        Page<Order> first = find("  MARIO   ROSSI  ", null, null, null, 0, 2);
        Page<Order> second = find("mario@example.test", null, null, null, 1, 2);
        List<UUID> expected = Stream.of(mixed, pending, cad).map(Order::getId)
                .sorted(Comparator.comparing(UUID::toString).reversed()).toList();
        assertEquals(3, first.getTotalElements());
        assertEquals(expected.subList(0, 2), ids(first));
        assertEquals(3, second.getTotalElements());
        assertEquals(expected.subList(2, 3), ids(second));
        assertEquals(List.of(mixed.getId()), ids(find(
                "mario@example.test", OrderStatus.PAID, PaymentStatus.RECEIVED, OrderType.MIXED, 0, 25)));
        assertEquals(List.of(pending.getId()), ids(find(
                "Mario", null, PaymentStatus.PENDING, OrderType.CALCULATOR, 0, 25)));
        assertEquals(List.of(cad.getId()), ids(find(
                "Referente Rossi", null, null, OrderType.CAD, 0, 25)));
        assertEquals(List.of(shop.getId()), ids(find(null, null, null, OrderType.SHOP, 0, 25)));
        assertEquals(List.of(mixed.getId()), ids(find("100%", null, null, null, 0, 25)));
        assertEquals(0, find("Studio 100_", null, null, null, 0, 25).getTotalElements());
        assertEquals(List.of(mixed.getId()), ids(find(mixed.getOrderNumber(), null, null, null, 0, 25)));
        assertEquals(List.of(mixed.getId()), ids(find(mixed.getId().toString(), null, null, null, 0, 25)));
    }

    private Page<Order> find(String q, OrderStatus orderStatus, PaymentStatus paymentStatus, OrderType type,
                             int page, int size) {
        var filters = new AdminOrderSearchQuery(q, orderStatus, paymentStatus, type);
        return orders.findAll(AdminOrderSearchSpecifications.from(filters), new AdminPageQuery(page, size).toPageable());
    }

    private List<UUID> ids(Page<Order> page) {
        return page.getContent().stream().map(Order::getId).toList();
    }

    private Order saveOrder(String email, String status, OffsetDateTime createdAt, boolean cad) {
        Order order = new Order();
        order.setCreatedAt(createdAt);
        order.setStatus(status);
        order.setCustomerEmail(email);
        order.setBillingCustomerType("PRIVATE");
        order.setBillingAddressLine1("Teststrasse 1");
        order.setBillingZip("8000");
        order.setBillingCity("Zurich");
        order.setBillingCountryCode("CH");
        order.setCurrency("CHF");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSubtotalChf(BigDecimal.TEN);
        order.setIsCadOrder(cad);
        order.setCadTotalChf(BigDecimal.ZERO);
        order.setTotalChf(BigDecimal.TEN);
        return orders.save(order);
    }

    private void savePayment(Order order, String status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod("OTHER");
        payment.setStatus(status);
        payment.setCurrency("CHF");
        payment.setAmountChf(BigDecimal.TEN);
        payment.setInitiatedAt(CREATED);
        payments.save(payment);
    }

    private void saveItem(Order order, String type) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setItemType(type);
        item.setOriginalFilename("fixture.stl");
        item.setStoredFilename("fixture.stl");
        item.setStoredRelativePath("test/fixture.stl");
        item.setMaterialCode("PLA");
        item.setUnitPriceChf(BigDecimal.TEN);
        item.setLineTotalChf(BigDecimal.TEN);
        items.save(item);
    }
}

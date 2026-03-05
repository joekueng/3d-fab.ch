package com.printcalculator.controller;

import com.printcalculator.dto.OrderDto;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.order.OrderControllerService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import com.printcalculator.service.payment.TwintPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerPrivacyTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private StorageService storageService;
    @Mock
    private InvoicePdfRenderingService invoiceService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private TwintPaymentService twintPaymentService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentRepository paymentRepo;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        OrderControllerService orderControllerService = new OrderControllerService(
                orderService,
                orderRepo,
                orderItemRepo,
                storageService,
                invoiceService,
                qrBillService,
                twintPaymentService,
                paymentService,
                paymentRepo
        );
        controller = new OrderController(orderControllerService);
    }

    @Test
    void getOrder_pendingPayment_keepsPersonalData() {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        ResponseEntity<OrderDto> response = controller.getOrder(orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("customer@example.com", response.getBody().getCustomerEmail());
        assertEquals("+41790000000", response.getBody().getCustomerPhone());
        assertNotNull(response.getBody().getBillingAddress());
    }

    @Test
    void getOrder_advancedStatuses_redactsPersonalData() {
        List<String> statuses = List.of("IN_PRODUCTION", "SHIPPED", "COMPLETED");

        for (String status : statuses) {
            UUID orderId = UUID.randomUUID();
            Order order = buildOrder(orderId, status);

            when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
            when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
            when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.empty());

            ResponseEntity<OrderDto> response = controller.getOrder(orderId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNull(response.getBody().getCustomerEmail());
            assertNull(response.getBody().getCustomerPhone());
            assertNull(response.getBody().getBillingCustomerType());
            assertNull(response.getBody().getBillingAddress());
            assertNull(response.getBody().getShippingAddress());
        }
    }

    private Order buildOrder(UUID orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(status);
        order.setCustomerEmail("customer@example.com");
        order.setCustomerPhone("+41790000000");
        order.setBillingCustomerType("PRIVATE");
        order.setBillingFirstName("Joe");
        order.setBillingLastName("Kung");
        order.setBillingAddressLine1("Via G. Pioda 1");
        order.setBillingZip("6900");
        order.setBillingCity("Lugano");
        order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(true);
        return order;
    }
}

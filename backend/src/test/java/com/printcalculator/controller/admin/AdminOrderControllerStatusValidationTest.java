package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminOrderStatusUpdateRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.order.AdminOrderControllerService;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerStatusValidationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private StorageService storageService;
    @Mock
    private InvoicePdfRenderingService invoicePdfRenderingService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminOrderController controller;

    @BeforeEach
    void setUp() {
        AdminOrderControllerService adminOrderControllerService = new AdminOrderControllerService(
                orderRepository,
                orderItemRepository,
                paymentRepository,
                quoteLineItemRepository,
                paymentService,
                storageService,
                invoicePdfRenderingService,
                qrBillService,
                eventPublisher
        );
        controller = new AdminOrderController(adminOrderControllerService);
    }

    @Test
    void updateOrderStatus_withInvalidStatus_shouldReturn400AndNotSave() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("PENDING_PAYMENT");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AdminOrderStatusUpdateRequest payload = new AdminOrderStatusUpdateRequest();
        payload.setStatus("REPORTED");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateOrderStatus(orderId, payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateOrderStatus_withValidStatus_shouldReturn200() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus("PENDING_PAYMENT");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrder_Id(orderId)).thenReturn(List.of());
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        AdminOrderStatusUpdateRequest payload = new AdminOrderStatusUpdateRequest();
        payload.setStatus("PAID");

        ResponseEntity<OrderDto> response = controller.updateOrderStatus(orderId, payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PAID", response.getBody().getStatus());
        verify(orderRepository).save(order);
    }
}

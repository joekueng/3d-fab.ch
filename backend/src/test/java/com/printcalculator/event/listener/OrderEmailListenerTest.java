package com.printcalculator.event.listener;

import com.printcalculator.entity.Customer;
import com.printcalculator.entity.Order;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.service.InvoicePdfRenderingService;
import com.printcalculator.service.QrBillService;
import com.printcalculator.service.StorageService;
import com.printcalculator.service.email.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEmailListenerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private InvoicePdfRenderingService invoicePdfRenderingService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private QrBillService qrBillService;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private OrderEmailListener orderEmailListener;

    @Captor
    private ArgumentCaptor<Map<String, Object>> templateDataCaptor;

    @Captor
    private ArgumentCaptor<byte[]> attachmentDataCaptor;

    private Order order;
    private OrderCreatedEvent event;

    @BeforeEach
    void setUp() throws Exception {
        Customer customer = new Customer();
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setEmail("john.doe@test.com");

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomer(customer);
        order.setCreatedAt(OffsetDateTime.parse("2026-02-21T10:00:00Z"));
        order.setTotalChf(new BigDecimal("150.50"));

        event = new OrderCreatedEvent(this, order);

        ReflectionTestUtils.setField(orderEmailListener, "adminMailEnabled", true);
        ReflectionTestUtils.setField(orderEmailListener, "adminMailAddress", "admin@printcalculator.local");
        ReflectionTestUtils.setField(orderEmailListener, "frontendBaseUrl", "https://3d-fab.ch");

        when(storageService.loadAsResource(any())).thenReturn(new ByteArrayResource("PDF".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void handleOrderCreatedEvent_ShouldSendCustomerAndAdminEmails() {
        orderEmailListener.handleOrderCreatedEvent(event);

        verify(emailNotificationService, times(1)).sendEmailWithAttachment(
                eq("john.doe@test.com"),
                eq("Conferma Ordine #" + order.getOrderNumber() + " - 3D-Fab"),
                eq("order-confirmation"),
                templateDataCaptor.capture(),
                eq("Conferma-Ordine-" + order.getOrderNumber() + ".pdf"),
                attachmentDataCaptor.capture()
        );

        Map<String, Object> customerData = templateDataCaptor.getValue();
        assertEquals("John", customerData.get("customerName"));
        assertEquals(order.getId(), customerData.get("orderId"));
        assertEquals(order.getOrderNumber(), customerData.get("orderNumber"));
        assertEquals("https://3d-fab.ch/it/co/" + order.getId(), customerData.get("orderDetailsUrl"));
        assertNotNull(customerData.get("orderDate"));
        assertTrue(customerData.get("orderDate").toString().contains("2026"));
        assertTrue(customerData.get("totalCost").toString().contains("150"));
        assertArrayEquals("PDF".getBytes(StandardCharsets.UTF_8), attachmentDataCaptor.getValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> adminTemplateCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(emailNotificationService, times(1)).sendEmail(
                eq("admin@printcalculator.local"),
                eq("Nuovo Ordine Ricevuto #" + order.getOrderNumber() + " - John Doe"),
                eq("order-confirmation"),
                adminTemplateCaptor.capture()
        );

        Map<String, Object> adminData = adminTemplateCaptor.getValue();
        assertEquals("John Doe", adminData.get("customerName"));
    }

    @Test
    void handleOrderCreatedEvent_WithAdminDisabled_ShouldOnlySendCustomerEmail() {
        ReflectionTestUtils.setField(orderEmailListener, "adminMailEnabled", false);

        orderEmailListener.handleOrderCreatedEvent(event);

        verify(emailNotificationService, times(1)).sendEmailWithAttachment(
                eq("john.doe@test.com"),
                anyString(),
                anyString(),
                anyMap(),
                anyString(),
                any()
        );

        verify(emailNotificationService, never()).sendEmail(
                eq("admin@printcalculator.local"),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void handleOrderCreatedEvent_ExceptionHandling_ShouldNotPropagate() {
        doThrow(new RuntimeException("Simulated Mail Failure"))
                .when(emailNotificationService).sendEmailWithAttachment(anyString(), anyString(), anyString(), anyMap(), anyString(), any());

        assertDoesNotThrow(() -> orderEmailListener.handleOrderCreatedEvent(event));

        verify(emailNotificationService, times(1))
                .sendEmailWithAttachment(anyString(), anyString(), anyString(), anyMap(), anyString(), any());
    }
}

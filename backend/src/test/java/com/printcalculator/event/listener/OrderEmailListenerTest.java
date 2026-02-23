package com.printcalculator.event.listener;

import com.printcalculator.entity.Customer;
import com.printcalculator.entity.Order;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.service.email.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEmailListenerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private OrderEmailListener orderEmailListener;

    @Captor
    private ArgumentCaptor<Map<String, Object>> templateDataCaptor;

    private Order order;
    private OrderCreatedEvent event;

    @BeforeEach
    void setUp() {
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
        ReflectionTestUtils.setField(orderEmailListener, "frontendBaseUrl", "https://tuosito.it");
    }

    @Test
    void handleOrderCreatedEvent_ShouldSendCustomerAndAdminEmails() {
        // Act
        orderEmailListener.handleOrderCreatedEvent(event);

        // Assert Customer Email
        verify(emailNotificationService, times(1)).sendEmail(
                eq("john.doe@test.com"),
                eq("Conferma Ordine #" + order.getOrderNumber() + " - 3D-Fab"),
                eq("order-confirmation"),
                templateDataCaptor.capture()
        );

        Map<String, Object> customerData = templateDataCaptor.getAllValues().get(0);
        assertEquals("John", customerData.get("customerName"));
        assertEquals(order.getId(), customerData.get("orderId"));
        assertEquals(order.getOrderNumber(), customerData.get("orderNumber"));
        assertEquals("https://tuosito.it/ordine/" + order.getId(), customerData.get("orderDetailsUrl"));
        assertEquals(order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), customerData.get("orderDate"));
        assertEquals("150.50", customerData.get("totalCost"));

        // Assert Admin Email
        verify(emailNotificationService, times(1)).sendEmail(
                eq("admin@printcalculator.local"),
                eq("Nuovo Ordine Ricevuto #" + order.getOrderNumber() + " - Doe"),
                eq("order-confirmation"),
                templateDataCaptor.capture()
        );

        Map<String, Object> adminData = templateDataCaptor.getAllValues().get(1);
        assertEquals("John Doe", adminData.get("customerName"));
    }
    
    @Test
    void handleOrderCreatedEvent_WithAdminDisabled_ShouldOnlySendCustomerEmail() {
        // Arrange
        ReflectionTestUtils.setField(orderEmailListener, "adminMailEnabled", false);
        
        // Act
        orderEmailListener.handleOrderCreatedEvent(event);

        // Assert
        verify(emailNotificationService, times(1)).sendEmail(
                eq("john.doe@test.com"),
                anyString(),
                anyString(),
                any()
        );
        
        verify(emailNotificationService, never()).sendEmail(
                eq("admin@printcalculator.local"),
                anyString(),
                anyString(),
                any()
        );
    }
    
    @Test
    void handleOrderCreatedEvent_ExceptionHandling_ShouldNotPropagate() {
        // Arrange
        doThrow(new RuntimeException("Simulated Mail Failure"))
            .when(emailNotificationService).sendEmail(anyString(), anyString(), anyString(), any());
            
        // Act & Assert
        // Event listener shouldn't throw exception back, thus passing the test.
        orderEmailListener.handleOrderCreatedEvent(event);
        
        verify(emailNotificationService, times(1)).sendEmail(anyString(), anyString(), anyString(), any());
    }
}

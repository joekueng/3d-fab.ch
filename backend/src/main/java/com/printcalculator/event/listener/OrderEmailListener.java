package com.printcalculator.event.listener;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.service.email.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private final EmailNotificationService emailNotificationService;

    @Value("${app.mail.admin.enabled:true}")
    private boolean adminMailEnabled;

    @Value("${app.mail.admin.address:}")
    private String adminMailAddress;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Async
    @EventListener
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        Order order = event.getOrder();
        log.info("Processing OrderCreatedEvent for order id: {}", order.getId());

        try {
            sendCustomerConfirmationEmail(order);
            
            if (adminMailEnabled && adminMailAddress != null && !adminMailAddress.isEmpty()) {
                sendAdminNotificationEmail(order);
            }
        } catch (Exception e) {
            log.error("Failed to process email notifications for order id: {}", order.getId(), e);
        }
    }

    @Async
    @EventListener
    public void handlePaymentReportedEvent(PaymentReportedEvent event) {
        Order order = event.getOrder();
        log.info("Processing PaymentReportedEvent for order id: {}", order.getId());

        try {
            sendPaymentReportedEmail(order);
        } catch (Exception e) {
            log.error("Failed to send payment reported email for order id: {}", order.getId(), e);
        }
    }

    private void sendCustomerConfirmationEmail(Order order) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName());
        templateData.put("orderId", order.getId());
        templateData.put("orderNumber", getDisplayOrderNumber(order));
        templateData.put("orderDetailsUrl", buildOrderDetailsUrl(order));
        templateData.put("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        templateData.put("totalCost", String.format("%.2f", order.getTotalChf()));

        emailNotificationService.sendEmail(
                order.getCustomer().getEmail(),
                "Conferma Ordine #" + getDisplayOrderNumber(order) + " - 3D-Fab",
                "order-confirmation",
                templateData
        );
    }

    private void sendPaymentReportedEmail(Order order) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName());
        templateData.put("orderId", order.getId());
        templateData.put("orderNumber", getDisplayOrderNumber(order));
        templateData.put("orderDetailsUrl", buildOrderDetailsUrl(order));

        emailNotificationService.sendEmail(
                order.getCustomer().getEmail(),
                "Stiamo verificando il tuo pagamento (Ordine #" + getDisplayOrderNumber(order) + ")",
                "payment-reported",
                templateData
        );
    }

    private void sendAdminNotificationEmail(Order order) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
        templateData.put("orderId", order.getId());
        templateData.put("orderNumber", getDisplayOrderNumber(order));
        templateData.put("orderDetailsUrl", buildOrderDetailsUrl(order));
        templateData.put("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        templateData.put("totalCost", String.format("%.2f", order.getTotalChf()));
        
        // Possiamo riutilizzare lo stesso template per ora o crearne uno ad-hoc in futuro
        emailNotificationService.sendEmail(
                adminMailAddress,
                "Nuovo Ordine Ricevuto #" + getDisplayOrderNumber(order) + " - " + order.getCustomer().getLastName(),
                "order-confirmation", 
                templateData
        );
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

    private String buildOrderDetailsUrl(Order order) {
        String baseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        return baseUrl + "/co/" + order.getId();
    }
}

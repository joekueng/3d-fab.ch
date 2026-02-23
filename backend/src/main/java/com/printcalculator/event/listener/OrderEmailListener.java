package com.printcalculator.event.listener;

import com.printcalculator.entity.Order;
import com.printcalculator.event.OrderCreatedEvent;
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

    private void sendCustomerConfirmationEmail(Order order) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName());
        templateData.put("orderId", order.getId());
        templateData.put("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        templateData.put("totalCost", String.format("%.2f", order.getTotalChf()));

        emailNotificationService.sendEmail(
                order.getCustomer().getEmail(),
                "Conferma Ordine #" + order.getId() + " - 3D-Fab",
                "order-confirmation",
                templateData
        );
    }

    private void sendAdminNotificationEmail(Order order) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName());
        templateData.put("orderId", order.getId());
        templateData.put("orderDate", order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        templateData.put("totalCost", String.format("%.2f", order.getTotalChf()));
        
        // Possiamo riutilizzare lo stesso template per ora o crearne uno ad-hoc in futuro
        emailNotificationService.sendEmail(
                adminMailAddress,
                "Nuovo Ordine Ricevuto #" + order.getId() + " - " + order.getCustomer().getLastName(),
                "order-confirmation", 
                templateData
        );
    }
}

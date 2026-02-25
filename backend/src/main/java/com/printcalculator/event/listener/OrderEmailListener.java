package com.printcalculator.event.listener;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.InvoicePdfRenderingService;
import com.printcalculator.service.QrBillService;
import com.printcalculator.repository.OrderItemRepository;
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
    private final InvoicePdfRenderingService invoicePdfRenderingService;
    private final OrderItemRepository orderItemRepository;
    private final QrBillService qrBillService;

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

    @Async
    @EventListener
    public void handlePaymentConfirmedEvent(PaymentConfirmedEvent event) {
        Order order = event.getOrder();
        Payment payment = event.getPayment();
        log.info("Processing PaymentConfirmedEvent for order id: {}", order.getId());

        try {
            sendPaidInvoiceEmail(order, payment);
        } catch (Exception e) {
            log.error("Failed to send paid invoice email for order id: {}", order.getId(), e);
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

    private void sendPaidInvoiceEmail(Order order, Payment payment) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("customerName", order.getCustomer().getFirstName());
        templateData.put("orderId", order.getId());
        templateData.put("orderNumber", getDisplayOrderNumber(order));
        templateData.put("orderDetailsUrl", buildOrderDetailsUrl(order));
        templateData.put("totalCost", String.format("%.2f", order.getTotalChf()));

        byte[] pdf = null;
        try {
            java.util.List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
            pdf = invoicePdfRenderingService.generateDocumentPdf(order, items, false, qrBillService, payment);
        } catch (Exception e) {
            log.error("Failed to generate PDF for paid invoice email: {}", e.getMessage(), e);
        }

        String filename = "Fattura-" + getDisplayOrderNumber(order) + ".pdf";

        emailNotificationService.sendEmailWithAttachment(
                order.getCustomer().getEmail(),
                "Fattura Pagata (Ordine #" + getDisplayOrderNumber(order) + ") - 3D-Fab",
                "payment-confirmed",
                templateData,
                filename,
                pdf
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

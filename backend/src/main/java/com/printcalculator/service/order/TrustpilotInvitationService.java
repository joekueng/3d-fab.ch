package com.printcalculator.service.order;

import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.EmailLogRepository;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.email.EmailSendResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrustpilotInvitationService {
    private static final List<String> DUPLICATE_BLOCKING_STATUSES = List.of("SENT", "UNKNOWN");

    private final EmailNotificationService emailNotificationService;
    private final EmailAuditService emailAuditService;
    private final EmailLogRepository emailLogRepository;

    @Value("${trustpilot.invitation-email:3d-fab.ch+b575265a17@invite.trustpilot.com}")
    private String invitationEmail;

    public TrustpilotInvitationService(EmailNotificationService emailNotificationService,
                                       EmailAuditService emailAuditService,
                                       EmailLogRepository emailLogRepository) {
        this.emailNotificationService = emailNotificationService;
        this.emailAuditService = emailAuditService;
        this.emailLogRepository = emailLogRepository;
    }

    @Transactional
    public EmailLog sendInvitation(Order order) {
        String customerEmail = order.getCustomerEmail() == null ? "" : order.getCustomerEmail().trim();
        if (customerEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no customer email");
        }
        if (!"SHIPPED".equalsIgnoreCase(order.getStatus()) && !"COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trustpilot invitations are available for shipped or completed orders");
        }
        if (emailLogRepository.existsByEventTypeAndRecipientIgnoreCaseAndStatusIn(
                EmailAuditService.EVENT_TRUSTPILOT_INVITATION, customerEmail, DUPLICATE_BLOCKING_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Trustpilot invitation was already sent to this customer");
        }

        String recipientName = customerName(order);
        String referenceId = shortReference(order);
        String subject = "Trustpilot invitation trigger";
        EmailSendResult result = emailNotificationService.sendEmail(invitationEmail, subject,
                "trustpilot-invitation", Map.of(
                        "recipientName", recipientName,
                        "recipientEmail", customerEmail,
                        "referenceId", referenceId
                ));
        return emailAuditService.recordOrderEmail(order,
                EmailAuditService.EVENT_TRUSTPILOT_INVITATION,
                EmailAuditService.ORIGIN_ADMIN,
                customerEmail,
                subject,
                "trustpilot-invitation",
                null,
                result,
                null);
    }

    public boolean wasSentToCustomer(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) return false;
        return emailLogRepository.existsByEventTypeAndRecipientIgnoreCaseAndStatusIn(
                EmailAuditService.EVENT_TRUSTPILOT_INVITATION,
                customerEmail.trim(), DUPLICATE_BLOCKING_STATUSES);
    }

    private String customerName(Order order) {
        String first = order.getCustomer() != null ? order.getCustomer().getFirstName() : null;
        String last = order.getCustomer() != null ? order.getCustomer().getLastName() : null;
        if (blank(first)) first = order.getBillingFirstName();
        if (blank(last)) last = order.getBillingLastName();
        String name = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        if (!name.isBlank()) return name;
        if (!blank(order.getBillingContactPerson())) return order.getBillingContactPerson().trim();
        if (!blank(order.getBillingCompanyName())) return order.getBillingCompanyName().trim();
        return "Customer";
    }

    private String shortReference(Order order) {
        String reference = order.getOrderNumber();
        if (reference == null || reference.isBlank()) {
            reference = order.getId().toString();
        }
        try {
            UUID.fromString(reference);
            return reference.substring(0, 8);
        } catch (IllegalArgumentException ignored) {
            return reference;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

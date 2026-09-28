package com.printcalculator.service.email;

import com.printcalculator.entity.*;
import com.printcalculator.event.listener.OrderEmailListener;
import com.printcalculator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentEmailDelivery {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentEmailJobRepository jobs;
    private final OrderEmailListener emails;

    @Transactional
    public boolean claim(UUID id, UUID orderId) {
        Order order = orders.findLockedById(orderId).orElse(null);
        PaymentEmailJob job = jobs.findLockedById(id).orElseThrow();
        if (!"PENDING".equals(job.getStatus()) || job.getDueAt().isAfter(OffsetDateTime.now())) return false;
        if (order == null || !eligible(job, order)) {
            job.setStatus("CANCELLED");
            return false;
        }
        job.setStatus("SENDING");
        job.setAttemptedAt(OffsetDateTime.now());
        return true;
    }

    @Transactional
    public void deliver(UUID id, UUID orderId) {
        // Same lock order as payment transitions. Recheck after the committed claim.
        Order order = orders.findLockedById(orderId).orElse(null);
        PaymentEmailJob job = jobs.findLockedById(id).orElseThrow();
        if (!"SENDING".equals(job.getStatus())) return;
        if (order == null || !eligible(job, order)) {
            job.setStatus("CANCELLED");
            return;
        }
        EmailLog result = "REPORTED".equals(job.getKind())
                ? emails.sendPaymentReportedEmail(order, EmailAuditService.ORIGIN_PAYMENT_OUTBOX, null)
                : emails.sendPaidInvoiceEmail(order, payments.findByOrder_Id(orderId).orElseThrow(),
                    EmailAuditService.ORIGIN_PAYMENT_OUTBOX, null);
        job.setStatus(result.getStatus());
        job.setEmailLogId(result.getId());
        job.setFinishedAt(OffsetDateTime.now());
        job.setResult(result.getErrorMessage() == null ? null : result.getErrorMessage().substring(0, Math.min(500, result.getErrorMessage().length())));
    }

    @Transactional
    public void markUncertain(UUID id) {
        jobs.findLockedById(id).filter(j -> "SENDING".equals(j.getStatus())).ifPresent(j -> {
            j.setStatus("UNKNOWN");
            j.setFinishedAt(OffsetDateTime.now());
            j.setResult("Interrupted delivery; inspect SMTP/audit before any manual resend.");
        });
    }

    private boolean eligible(PaymentEmailJob job, Order order) {
        Payment payment = payments.findByOrder_Id(order.getId()).orElse(null);
        if (payment == null) return false;
        if ("REPORTED".equals(job.getKind())) {
            return "PENDING_PAYMENT".equals(order.getStatus()) && "REPORTED".equals(payment.getStatus());
        }
        return "RECEIVED".equals(payment.getStatus()) || "COMPLETED".equals(payment.getStatus());
    }
}

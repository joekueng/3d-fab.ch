package com.printcalculator.service.email;

import com.printcalculator.entity.PaymentEmailJob;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.repository.PaymentEmailJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentEmailOutbox {
    private final PaymentEmailJobRepository jobs;
    @Value("${app.payment.reported-email-delay:PT5M}")
    private Duration reportedDelay;

    // Synchronous: enqueue/cancel commits atomically with the payment, never send here.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void reported(PaymentReportedEvent event) {
        enqueue(event.getOrder().getId(), "REPORTED", event.getPayment().getReportedAt().plus(reportedDelay));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmed(PaymentConfirmedEvent event) {
        jobs.cancelReported(event.getOrder().getId());
        enqueue(event.getOrder().getId(), "CONFIRMED", OffsetDateTime.now());
    }

    private void enqueue(UUID orderId, String kind, OffsetDateTime due) {
        if (jobs.existsByOrderIdAndKind(orderId, kind)) return;
        PaymentEmailJob job = new PaymentEmailJob();
        job.setOrderId(orderId);
        job.setKind(kind);
        job.setDueAt(due);
        jobs.save(job);
    }
}

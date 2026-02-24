package com.printcalculator.service;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepo,
                          OrderRepository orderRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment getOrCreatePaymentForOrder(Order order, String defaultMethod) {
        Optional<Payment> existing = paymentRepo.findByOrder_Id(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        // Default to "OTHER" always, as payment method should only be set by the admin explicitly
        payment.setMethod("OTHER");
        payment.setStatus("PENDING");
        payment.setCurrency(order.getCurrency() != null ? order.getCurrency() : "CHF");
        payment.setAmountChf(order.getTotalChf() != null ? order.getTotalChf() : BigDecimal.ZERO);
        payment.setInitiatedAt(OffsetDateTime.now());

        return paymentRepo.save(payment);
    }

    @Transactional
    public Payment reportPayment(UUID orderId, String method) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, "OTHER"));

        if (!"PENDING".equals(payment.getStatus())) {
            throw new IllegalStateException("Payment is not in PENDING state. Current state: " + payment.getStatus());
        }

        payment.setStatus("REPORTED");
        payment.setReportedAt(OffsetDateTime.now());
        
        // We intentionally do not update the payment method here based on user input,
        // because the user cannot reliably determine the actual method without an integration.
        // It will be updated by the backoffice admin manually.

        payment = paymentRepo.save(payment);

        eventPublisher.publishEvent(new PaymentReportedEvent(this, order, payment));

        return payment;
    }
}

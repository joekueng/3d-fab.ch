package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.event.PaymentConfirmedEvent;
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

    private static final String RECEIVED_STATUS = "RECEIVED";
    private static final String LEGACY_COMPLETED_STATUS = "COMPLETED";

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
        // The confirmed method is set by the admin or an authenticated provider receipt.
        payment.setMethod("OTHER");
        payment.setStatus("PENDING");
        payment.setCurrency(order.getCurrency() != null ? order.getCurrency() : "CHF");
        payment.setAmountChf(order.getTotalChf() != null ? order.getTotalChf() : BigDecimal.ZERO);
        payment.setInitiatedAt(OffsetDateTime.now());

        return paymentRepo.save(payment);
    }

    @Transactional
    public Payment reportPayment(UUID orderId, String method) {
        Order order = orderRepo.findLockedById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, "OTHER"));

        if (!"PENDING_PAYMENT".equals(order.getStatus()) || !"PENDING".equals(payment.getStatus())) {
            return payment;
        }

        payment.setStatus("REPORTED");
        payment.setReportedAt(OffsetDateTime.now());
        
        // A customer report does not establish the actual payment method.

        payment = paymentRepo.save(payment);

        eventPublisher.publishEvent(new PaymentReportedEvent(this, order, payment));

        return payment;
    }

    @Transactional
    public Payment confirmPayment(UUID orderId, String method) {
        Order order = orderRepo.findLockedById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, method != null ? method : "OTHER"));

        if (RECEIVED_STATUS.equals(payment.getStatus()) || LEGACY_COMPLETED_STATUS.equals(payment.getStatus())) {
            return payment;
        }

        if (!"PENDING_PAYMENT".equals(order.getStatus())
                || !("PENDING".equals(payment.getStatus()) || "REPORTED".equals(payment.getStatus()))) {
            throw new IllegalStateException("Order/payment requires manual review before confirmation");
        }

        payment.setStatus(RECEIVED_STATUS);
        if (method != null && !method.isBlank()) {
            payment.setMethod(method.toUpperCase());
        }
        payment.setReceivedAt(OffsetDateTime.now());
        payment = paymentRepo.save(payment);

        order.setStatus("PAID");
        order.setPaidAt(OffsetDateTime.now());
        orderRepo.save(order);

        eventPublisher.publishEvent(new PaymentConfirmedEvent(this, order, payment));

        return payment;
    }

    @Transactional
    public Payment updatePaymentMethod(UUID orderId, String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }

        Order order = orderRepo.findLockedById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id " + orderId));

        Payment payment = paymentRepo.findByOrder_Id(orderId)
                .orElseGet(() -> getOrCreatePaymentForOrder(order, "OTHER"));

        payment.setMethod(method.trim().toUpperCase());
        return paymentRepo.save(payment);
    }
}

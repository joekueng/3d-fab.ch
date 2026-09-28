package com.printcalculator.service.payment.twint;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TwintReconciliationService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final TwintReceiptRepository receipts;
    private final PaymentService paymentService;
    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)(?<![a-z0-9-])([a-f0-9]{8}(?:-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})?)(?![a-z0-9-])");

    // The mailbox cursor lock serializes receipt/transaction claims across server instances.
    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcile(TwintReceipt receipt, TwintNotificationParser.Notification notification) {
        receipt.setPaymentMessage(notification.message());
        receipt.setAmount(notification.amount());
        receipt.setTransactionId(notification.transactionId());
        receipt.setTransactionAt(notification.transactionAt());
        var matcher = REFERENCE.matcher(notification.message());
        if (!matcher.find()) { receipt.setOutcome("MISSING_REFERENCE"); return; }
        String reference = matcher.group(1).toLowerCase(Locale.ROOT);
        if (matcher.find()) { receipt.setOutcome("AMBIGUOUS_REFERENCE"); return; }
        UUID id = reference.length() == 36 ? UUID.fromString(reference) : null;
        if (id != null && orders.existsById(id)) {
            receipt.setMatchType("UUID");
        } else {
            List<UUID> matches = orders.findIdsByUuidPrefix(reference.substring(0, 8));
            if (matches.size() != 1) {
                receipt.setOutcome(matches.isEmpty() ? "UNKNOWN_REFERENCE" : "AMBIGUOUS_REFERENCE");
                return;
            }
            id = matches.getFirst();
            receipt.setMatchType("PREFIX");
        }
        receipt.setOrderId(id);
        Order order = orders.findLockedById(id).orElseThrow();
        if (order.getTotalChf() == null || order.getTotalChf().compareTo(notification.amount()) != 0) {
            receipt.setOutcome("AMOUNT_MISMATCH"); return;
        }
        if (notification.transactionId() != null) {
            var previous = receipts.findByClaimedTransactionId(notification.transactionId());
            if (previous.isPresent()) {
                receipt.setOutcome(id.equals(previous.get().getOrderId()) ? "DUPLICATE_TRANSACTION" : "TRANSACTION_CONFLICT");
                return;
            }
            receipt.setClaimedTransactionId(notification.transactionId());
            if (receipts.existsByOrderIdAndClaimedTransactionIdIsNotNull(id)) {
                receipt.setOutcome("POSSIBLE_DOUBLE_PAYMENT"); return;
            }
        } else if (receipts.existsByContentHashAndOutcomeNot(receipt.getContentHash(), "AUTHENTICITY_REVIEW")) {
            receipt.setOutcome("DUPLICATE_MESSAGE"); return;
        }
        Payment payment = payments.findByOrder_Id(id).orElse(null);
        if ("CANCELLED".equals(order.getStatus()) || (payment != null && "REFUNDED".equals(payment.getStatus()))) {
            receipt.setOutcome("CANCELLED_OR_REFUNDED"); return;
        }
        if (payment != null && Set.of("RECEIVED", "COMPLETED").contains(payment.getStatus())) {
            receipt.setOutcome("TWINT".equals(payment.getMethod()) ? "ALREADY_CONFIRMED" : "CONFIRMED_OTHER_METHOD");
            return;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())
                || (payment != null && !Set.of("PENDING", "REPORTED").contains(payment.getStatus()))) {
            receipt.setOutcome("INCONSISTENT_STATE"); return;
        }
        Payment confirmed = paymentService.confirmPayment(id, "TWINT");
        confirmed.setProviderTransactionId(notification.transactionId());
        payments.save(confirmed);
        receipt.setOutcome("CONFIRMED");
    }
}

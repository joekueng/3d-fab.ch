package com.printcalculator.service.payment.twint;

import com.printcalculator.entity.*;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.*;
import com.printcalculator.service.payment.PaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwintReconciliationServiceTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock TwintReceiptRepository receipts;
    @Mock PaymentService paymentService;
    @InjectMocks TwintReconciliationService service;
    final UUID id = UUID.fromString("abcdef12-1234-1234-1234-123456789abc");
    Order order;
    Payment payment;
    TwintReceipt receipt;

    @BeforeEach void setup() {
        order = new Order(); order.setId(id); order.setStatus("PENDING_PAYMENT"); order.setTotalChf(new BigDecimal("23.90"));
        payment = new Payment(); payment.setStatus("PENDING"); payment.setMethod("OTHER");
        receipt = new TwintReceipt(); receipt.setContentHash("test-content");
    }
    void matched() {
        when(orders.existsById(id)).thenReturn(true);
        when(orders.findLockedById(id)).thenReturn(Optional.of(order));
    }
    void reconcile(String message, String amount, String transaction) {
        service.reconcile(receipt, new TwintNotificationParser.Notification(message, new BigDecimal(amount), transaction, null));
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "REPORTED"})
    void confirmsWithoutRelyingOnReportedMethod(String status) {
        matched(); payment.setStatus(status);
        when(payments.findByOrder_Id(id)).thenReturn(Optional.of(payment));
        when(paymentService.confirmPayment(id, "TWINT")).thenReturn(payment);
        reconcile(id.toString(), "23.9", "transaction-1");
        assertEquals("CONFIRMED", receipt.getOutcome());
        assertEquals("UUID", receipt.getMatchType());
        assertEquals("transaction-1", payment.getProviderTransactionId());
    }
    @Test void mismatchNeverFallsBackToPrefix() {
        matched(); reconcile(id.toString(), "24.00", "tx");
        assertEquals("AMOUNT_MISMATCH", receipt.getOutcome());
        verify(orders, never()).findIdsByUuidPrefix(any());
        verifyNoInteractions(paymentService);
    }
    @Test void uniquePrefixMatchesEvenWithoutTransactionId() {
        when(orders.findIdsByUuidPrefix("abcdef12")).thenReturn(List.of(id));
        when(orders.findLockedById(id)).thenReturn(Optional.of(order));
        when(payments.findByOrder_Id(id)).thenReturn(Optional.of(payment));
        when(paymentService.confirmPayment(id, "TWINT")).thenReturn(payment);
        reconcile("Ordine #abcdef12", "23.90", null);
        assertEquals("PREFIX", receipt.getMatchType());
        assertEquals("CONFIRMED", receipt.getOutcome());
    }
    @Test void unknownFullUuidFallsBackToUniquePrefix() {
        when(orders.findIdsByUuidPrefix("abcdef12")).thenReturn(List.of(id));
        when(orders.findLockedById(id)).thenReturn(Optional.of(order));
        when(paymentService.confirmPayment(id, "TWINT")).thenReturn(payment);
        reconcile("abcdef12-0000-0000-0000-000000000000", "23.90", "tx");
        assertEquals("PREFIX", receipt.getMatchType());
        assertEquals("CONFIRMED", receipt.getOutcome());
    }
    @Test void ambiguousPrefixIsNeverPaid() {
        when(orders.findIdsByUuidPrefix("abcdef12")).thenReturn(List.of(id, UUID.randomUUID()));
        reconcile("abcdef12", "23.90", "tx");
        assertEquals("AMBIGUOUS_REFERENCE", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @Test void missingReferenceNeverMatchesByAmount() {
        reconcile("Thank you", "23.90", id.toString());
        assertEquals("MISSING_REFERENCE", receipt.getOutcome());
        verifyNoInteractions(orders, paymentService);
    }
    @Test void differentTransactionFlagsDoublePayment() {
        matched(); when(receipts.existsByOrderIdAndClaimedTransactionIdIsNotNull(id)).thenReturn(true);
        reconcile(id.toString(), "23.90", "second-tx");
        assertEquals("POSSIBLE_DOUBLE_PAYMENT", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @Test void transactionCannotConfirmAnotherOrder() {
        matched(); TwintReceipt old = new TwintReceipt(); old.setOrderId(UUID.randomUUID());
        when(receipts.findByClaimedTransactionId("tx")).thenReturn(Optional.of(old));
        reconcile(id.toString(), "23.90", "tx");
        assertEquals("TRANSACTION_CONFLICT", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @Test void duplicateIsRecordedWithoutConfirmingAgain() {
        matched(); TwintReceipt old = new TwintReceipt(); old.setOrderId(id);
        when(receipts.findByClaimedTransactionId("tx")).thenReturn(Optional.of(old));
        reconcile(id.toString(), "23.90", "tx");
        assertEquals("DUPLICATE_TRANSACTION", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @ParameterizedTest @ValueSource(strings = {"PAID", "IN_PRODUCTION", "SHIPPED", "COMPLETED"})
    void lateNotificationPreservesManualPayment(String status) {
        matched(); order.setStatus(status); payment.setStatus("RECEIVED"); payment.setMethod("BANK_TRANSFER");
        when(payments.findByOrder_Id(id)).thenReturn(Optional.of(payment));
        reconcile(id.toString(), "23.90", "late-tx");
        assertEquals("CONFIRMED_OTHER_METHOD", receipt.getOutcome());
        assertEquals(status, order.getStatus()); assertEquals("BANK_TRANSFER", payment.getMethod());
        verifyNoInteractions(paymentService);
    }
    @ParameterizedTest @ValueSource(strings = {"PAID", "IN_PRODUCTION", "SHIPPED", "COMPLETED"})
    void advancedUnpaidOrderRequiresReview(String status) {
        matched(); order.setStatus(status);
        when(payments.findByOrder_Id(id)).thenReturn(Optional.of(payment));
        reconcile(id.toString(), "23.90", "tx");
        assertEquals("INCONSISTENT_STATE", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @Test void cancelledOrderCannotBeReactivated() {
        matched(); order.setStatus("CANCELLED");
        reconcile(id.toString(), "23.90", "tx");
        assertEquals("CANCELLED_OR_REFUNDED", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
    @Test void refundCannotBeReactivated() {
        matched(); payment.setStatus("REFUNDED");
        when(payments.findByOrder_Id(id)).thenReturn(Optional.of(payment));
        reconcile(id.toString(), "23.90", "tx");
        assertEquals("CANCELLED_OR_REFUNDED", receipt.getOutcome());
        verifyNoInteractions(paymentService);
    }
}

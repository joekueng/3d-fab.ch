package com.printcalculator.service.email;

import com.printcalculator.entity.*;
import com.printcalculator.entity.Order;
import com.printcalculator.event.listener.OrderEmailListener;
import com.printcalculator.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEmailDeliveryTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock PaymentEmailJobRepository jobs;
    @Mock OrderEmailListener emails;
    @InjectMocks PaymentEmailDelivery delivery;
    Order order; Payment payment; PaymentEmailJob job;
    @BeforeEach void setup() {
        order = new Order(); order.setId(UUID.randomUUID()); order.setStatus("PENDING_PAYMENT");
        payment = new Payment(); payment.setStatus("REPORTED");
        job = new PaymentEmailJob(); job.setId(UUID.randomUUID()); job.setOrderId(order.getId());
        job.setKind("REPORTED"); job.setDueAt(OffsetDateTime.now().minusSeconds(1));
        lenient().when(orders.findLockedById(order.getId())).thenReturn(Optional.of(order));
        when(jobs.findLockedById(job.getId())).thenReturn(Optional.of(job));
        lenient().when(payments.findByOrder_Id(order.getId())).thenReturn(Optional.of(payment));
    }
    @Test void futureJobCannotSend() {
        job.setDueAt(OffsetDateTime.now().plusMinutes(1));
        assertFalse(delivery.claim(job.getId(), order.getId()));
        verifyNoInteractions(emails);
    }
    @Test void pendingReportSendsOnceAfterDelay() {
        assertTrue(delivery.claim(job.getId(), order.getId()));
        EmailLog log = new EmailLog(); log.setId(UUID.randomUUID()); log.setStatus("SENT");
        when(emails.sendPaymentReportedEmail(order, EmailAuditService.ORIGIN_PAYMENT_OUTBOX, null)).thenReturn(log);
        delivery.deliver(job.getId(), order.getId());
        assertEquals("SENT", job.getStatus()); assertEquals(log.getId(), job.getEmailLogId());
        delivery.deliver(job.getId(), order.getId());
        verify(emails, times(1)).sendPaymentReportedEmail(any(), any(), any());
    }
    @Test void confirmationBetweenClaimAndSendCancelsReport() {
        assertTrue(delivery.claim(job.getId(), order.getId()));
        payment.setStatus("RECEIVED"); order.setStatus("PAID");
        delivery.deliver(job.getId(), order.getId());
        assertEquals("CANCELLED", job.getStatus()); verifyNoInteractions(emails);
    }
    @Test void interruptedClaimIsUncertainAndNeverAutomaticallyReclaimed() {
        assertTrue(delivery.claim(job.getId(), order.getId()));
        delivery.markUncertain(job.getId());
        assertEquals("UNKNOWN", job.getStatus());
        assertFalse(delivery.claim(job.getId(), order.getId()));
        verifyNoInteractions(emails);
    }
    @Test void reportedJobForCancelledOrderNeverSends() {
        order.setStatus("CANCELLED");
        assertFalse(delivery.claim(job.getId(), order.getId()));
        assertEquals("CANCELLED", job.getStatus());
        verifyNoInteractions(emails);
    }
}

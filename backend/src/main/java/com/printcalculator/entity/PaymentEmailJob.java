package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Transactional outbox; one automatic message of each kind per order. */
@Entity
@Getter
@Setter
@Table(name = "payment_email_jobs", uniqueConstraints = @UniqueConstraint(
        name = "uq_payment_email_order_kind", columnNames = {"order_id", "kind"}),
        indexes = @Index(name = "ix_payment_email_due", columnList = "status,due_at"))
public class PaymentEmailJob {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false, length = 24)
    private String kind;
    @Column(nullable = false, length = 24)
    private String status = "PENDING";
    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;
    private OffsetDateTime attemptedAt;
    private OffsetDateTime finishedAt;
    private UUID emailLogId;
    @Column(length = 500)
    private String result;
}

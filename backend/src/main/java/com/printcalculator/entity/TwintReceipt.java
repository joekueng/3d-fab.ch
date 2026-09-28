package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "twint_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_twint_delivery", columnNames = {"mailbox_key", "uid_validity", "message_uid"}),
        @UniqueConstraint(name = "uq_twint_claimed_transaction", columnNames = "claimed_transaction_id")})
public class TwintReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "mailbox_key", nullable = false)
    private String mailboxKey;
    @Column(name = "uid_validity", nullable = false)
    private long uidValidity;
    @Column(name = "message_uid", nullable = false)
    private long messageUid;
    @Column(nullable = false, length = 64)
    private String contentHash;
    private UUID orderId;
    private String matchType;
    @Column(length = 1000)
    private String paymentMessage;
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;
    private String transactionId;
    // Only the first authenticated receipt claims a transaction. Duplicates keep transactionId above.
    @Column(name = "claimed_transaction_id")
    private String claimedTransactionId;
    private OffsetDateTime transactionAt;
    @Column(nullable = false)
    private OffsetDateTime acquiredAt = OffsetDateTime.now();
    @Column(nullable = false, length = 64)
    private String outcome;
}

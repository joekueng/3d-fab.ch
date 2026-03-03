package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "ix_payments_order",
                columnList = "order_id"),
        @Index(name = "ix_payments_reference",
                columnList = "payment_reference")})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payment_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "method", nullable = false, length = Integer.MAX_VALUE)
    private String method;

    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @ColumnDefault("'CHF'")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountChf;

    @Column(name = "payment_reference", length = Integer.MAX_VALUE)
    private String paymentReference;

    @Column(name = "provider_transaction_id", length = Integer.MAX_VALUE)
    private String providerTransactionId;

    @Column(name = "qr_payload", length = Integer.MAX_VALUE)
    private String qrPayload;

    @ColumnDefault("now()")
    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt;

    @Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmountChf() {
        return amountChf;
    }

    public void setAmountChf(BigDecimal amountChf) {
        this.amountChf = amountChf;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public OffsetDateTime getInitiatedAt() {
        return initiatedAt;
    }

    public void setInitiatedAt(OffsetDateTime initiatedAt) {
        this.initiatedAt = initiatedAt;
    }

    public OffsetDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(OffsetDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

}
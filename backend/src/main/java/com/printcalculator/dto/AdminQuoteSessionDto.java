package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;

public class AdminQuoteSessionDto {
    private UUID id;
    private String status;
    private String sessionType;
    private String materialCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private UUID convertedOrderId;
    private UUID sourceRequestId;
    private BigDecimal cadHours;
    private BigDecimal cadHourlyRateChf;
    private BigDecimal cadTotalChf;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getConvertedOrderId() {
        return convertedOrderId;
    }

    public void setConvertedOrderId(UUID convertedOrderId) {
        this.convertedOrderId = convertedOrderId;
    }

    public UUID getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(UUID sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public BigDecimal getCadHours() {
        return cadHours;
    }

    public void setCadHours(BigDecimal cadHours) {
        this.cadHours = cadHours;
    }

    public BigDecimal getCadHourlyRateChf() {
        return cadHourlyRateChf;
    }

    public void setCadHourlyRateChf(BigDecimal cadHourlyRateChf) {
        this.cadHourlyRateChf = cadHourlyRateChf;
    }

    public BigDecimal getCadTotalChf() {
        return cadTotalChf;
    }

    public void setCadTotalChf(BigDecimal cadTotalChf) {
        this.cadTotalChf = cadTotalChf;
    }
}

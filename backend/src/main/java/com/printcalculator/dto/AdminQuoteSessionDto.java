package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminQuoteSessionDto {
    private UUID id;
    private String status;
    private String materialCode;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private UUID convertedOrderId;

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
}

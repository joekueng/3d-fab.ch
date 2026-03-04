package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminCadInvoiceDto {
    private UUID sessionId;
    private String sessionStatus;
    private UUID sourceRequestId;
    private BigDecimal cadHours;
    private BigDecimal cadHourlyRateChf;
    private BigDecimal cadTotalChf;
    private BigDecimal printItemsTotalChf;
    private BigDecimal setupCostChf;
    private BigDecimal shippingCostChf;
    private BigDecimal grandTotalChf;
    private UUID convertedOrderId;
    private String convertedOrderStatus;
    private String checkoutPath;
    private String notes;
    private OffsetDateTime createdAt;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
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

    public BigDecimal getPrintItemsTotalChf() {
        return printItemsTotalChf;
    }

    public void setPrintItemsTotalChf(BigDecimal printItemsTotalChf) {
        this.printItemsTotalChf = printItemsTotalChf;
    }

    public BigDecimal getSetupCostChf() {
        return setupCostChf;
    }

    public void setSetupCostChf(BigDecimal setupCostChf) {
        this.setupCostChf = setupCostChf;
    }

    public BigDecimal getShippingCostChf() {
        return shippingCostChf;
    }

    public void setShippingCostChf(BigDecimal shippingCostChf) {
        this.shippingCostChf = shippingCostChf;
    }

    public BigDecimal getGrandTotalChf() {
        return grandTotalChf;
    }

    public void setGrandTotalChf(BigDecimal grandTotalChf) {
        this.grandTotalChf = grandTotalChf;
    }

    public UUID getConvertedOrderId() {
        return convertedOrderId;
    }

    public void setConvertedOrderId(UUID convertedOrderId) {
        this.convertedOrderId = convertedOrderId;
    }

    public String getConvertedOrderStatus() {
        return convertedOrderStatus;
    }

    public void setConvertedOrderStatus(String convertedOrderStatus) {
        this.convertedOrderStatus = convertedOrderStatus;
    }

    public String getCheckoutPath() {
        return checkoutPath;
    }

    public void setCheckoutPath(String checkoutPath) {
        this.checkoutPath = checkoutPath;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

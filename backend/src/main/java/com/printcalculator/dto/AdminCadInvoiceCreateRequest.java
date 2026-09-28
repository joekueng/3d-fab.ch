package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class AdminCadInvoiceCreateRequest {
    @jakarta.validation.constraints.Size(max = 160)
    private String clientName;
    public String getClientName() { return clientName; }
    public void setClientName(String value) { clientName = value; }

    @jakarta.validation.Valid
    @jakarta.validation.constraints.Size(min = 1)
    private java.util.List<@jakarta.validation.constraints.NotNull ServiceLineDto> serviceLines;
    public java.util.List<com.printcalculator.dto.ServiceLineDto> getServiceLines() { return serviceLines; }
    public void setServiceLines(java.util.List<com.printcalculator.dto.ServiceLineDto> lines) { serviceLines = lines; }

    @jakarta.validation.constraints.Size(max = 160)
    private String invoiceName;

    public String getInvoiceName() { return invoiceName; }
    public void setInvoiceName(String value) { invoiceName = value; }

    @jakarta.validation.constraints.Size(max = 160)
    private String collaborationName;

    public String getCollaborationName() { return collaborationName; }
    public void setCollaborationName(String value) { collaborationName = value; }

    private UUID sessionId;
    private UUID sourceRequestId;
    private BigDecimal cadHours;
    private BigDecimal cadHourlyRateChf;
    private String notes;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

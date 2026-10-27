package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Public order tracking and checkout response; customer contact/address data belongs to the admin API. */
public class PublicOrderDto {
    private com.printcalculator.dto.ServiceQuoteReference serviceQuoteReference;
    public com.printcalculator.dto.ServiceQuoteReference getServiceQuoteReference() { return serviceQuoteReference; }
    public void setServiceQuoteReference(com.printcalculator.dto.ServiceQuoteReference value) { serviceQuoteReference=value; }

    private String invoiceName;
    public String getInvoiceName() { return invoiceName; }
    public void setInvoiceName(String name) { invoiceName = name; }

    private java.util.List<com.printcalculator.dto.ServiceLineDto> serviceLines;
    public java.util.List<com.printcalculator.dto.ServiceLineDto> getServiceLines() { return serviceLines == null ? java.util.List.of() : java.util.List.copyOf(serviceLines); }
    public void setServiceLines(java.util.List<com.printcalculator.dto.ServiceLineDto> lines) { serviceLines = lines == null ? null : java.util.List.copyOf(lines); }

    private String informationToken;
    public String getInformationToken() { return informationToken; }
    public void setInformationToken(String token) { informationToken = token; }
    private UUID id;
    private String orderNumber;
    private String sourceType;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String preferredLanguage;
    private String currency;
    private BigDecimal setupCostChf;
    private BigDecimal shippingCostChf;
    private BigDecimal discountChf;
    private BigDecimal subtotalChf;
    private Boolean isCadOrder;
    private BigDecimal cadHours;
    private BigDecimal cadHourlyRateChf;
    private BigDecimal cadTotalChf;
    private Integer cadFileCount;
    private Boolean cadFileDownloadAvailable;
    private List<OrderDeliverableFileDto> cadFiles;
    private BigDecimal totalChf;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;
    private List<OrderItemDto> items;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSetupCostChf() { return setupCostChf; }
    public void setSetupCostChf(BigDecimal setupCostChf) { this.setupCostChf = setupCostChf; }

    public BigDecimal getShippingCostChf() { return shippingCostChf; }
    public void setShippingCostChf(BigDecimal shippingCostChf) { this.shippingCostChf = shippingCostChf; }

    public BigDecimal getDiscountChf() { return discountChf; }
    public void setDiscountChf(BigDecimal discountChf) { this.discountChf = discountChf; }

    public BigDecimal getSubtotalChf() { return subtotalChf; }
    public void setSubtotalChf(BigDecimal subtotalChf) { this.subtotalChf = subtotalChf; }

    public Boolean getIsCadOrder() { return isCadOrder; }
    public void setIsCadOrder(Boolean isCadOrder) { this.isCadOrder = isCadOrder; }

    public BigDecimal getCadHours() { return cadHours; }
    public void setCadHours(BigDecimal cadHours) { this.cadHours = cadHours; }

    public BigDecimal getCadHourlyRateChf() { return cadHourlyRateChf; }
    public void setCadHourlyRateChf(BigDecimal cadHourlyRateChf) { this.cadHourlyRateChf = cadHourlyRateChf; }

    public BigDecimal getCadTotalChf() { return cadTotalChf; }
    public void setCadTotalChf(BigDecimal cadTotalChf) { this.cadTotalChf = cadTotalChf; }

    public Integer getCadFileCount() { return cadFileCount; }
    public void setCadFileCount(Integer cadFileCount) { this.cadFileCount = cadFileCount; }

    public Boolean getCadFileDownloadAvailable() { return cadFileDownloadAvailable; }
    public void setCadFileDownloadAvailable(Boolean cadFileDownloadAvailable) { this.cadFileDownloadAvailable = cadFileDownloadAvailable; }

    public List<OrderDeliverableFileDto> getCadFiles() { return cadFiles; }
    public void setCadFiles(List<OrderDeliverableFileDto> cadFiles) { this.cadFiles = cadFiles; }

    public BigDecimal getTotalChf() { return totalChf; }
    public void setTotalChf(BigDecimal totalChf) { this.totalChf = totalChf; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}

package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class OrderDto {
    private UUID id;
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private String customerEmail;
    private String customerPhone;
    private String billingCustomerType;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private Boolean shippingSameAsBilling;
    private String currency;
    private BigDecimal setupCostChf;
    private BigDecimal shippingCostChf;
    private BigDecimal discountChf;
    private BigDecimal subtotalChf;
    private BigDecimal totalChf;
    private OffsetDateTime createdAt;
    private List<OrderItemDto> items;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getBillingCustomerType() { return billingCustomerType; }
    public void setBillingCustomerType(String billingCustomerType) { this.billingCustomerType = billingCustomerType; }

    public AddressDto getBillingAddress() { return billingAddress; }
    public void setBillingAddress(AddressDto billingAddress) { this.billingAddress = billingAddress; }

    public AddressDto getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(AddressDto shippingAddress) { this.shippingAddress = shippingAddress; }

    public Boolean getShippingSameAsBilling() { return shippingSameAsBilling; }
    public void setShippingSameAsBilling(Boolean shippingSameAsBilling) { this.shippingSameAsBilling = shippingSameAsBilling; }

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

    public BigDecimal getTotalChf() { return totalChf; }
    public void setTotalChf(BigDecimal totalChf) { this.totalChf = totalChf; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public List<OrderItemDto> getItems() { return items; }
    public void setItems(List<OrderItemDto> items) { this.items = items; }
}

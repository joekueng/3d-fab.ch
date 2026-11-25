package com.printcalculator.dto;

import jakarta.validation.constraints.Size;

public record AdminOrderSearchQuery(
        @Size(max = 320) String q,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        OrderType orderType
) {
    public enum OrderStatus { ALL, PENDING_PAYMENT, PAID, IN_PRODUCTION, SHIPPED, COMPLETED, CANCELLED }
    public enum PaymentStatus { ALL, PENDING, REPORTED, RECEIVED, COMPLETED }
    public enum OrderType { ALL, CAD, SHOP, CALCULATOR, MIXED }
}

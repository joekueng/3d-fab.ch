package com.printcalculator.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private CustomerDto customer;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private boolean shippingSameAsBilling;
}

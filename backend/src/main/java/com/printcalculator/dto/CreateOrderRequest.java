package com.printcalculator.dto;

import lombok.Data;
import jakarta.validation.constraints.AssertTrue;

@Data
public class CreateOrderRequest {
    private CustomerDto customer;
    private AddressDto billingAddress;
    private AddressDto shippingAddress;
    private boolean shippingSameAsBilling;

    @AssertTrue(message = "L'accettazione dei Termini e Condizioni e obbligatoria.")
    private boolean acceptTerms;

    @AssertTrue(message = "L'accettazione dell'Informativa Privacy e obbligatoria.")
    private boolean acceptPrivacy;
}

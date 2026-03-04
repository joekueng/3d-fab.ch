package com.printcalculator.dto;

import lombok.Data;
import jakarta.validation.constraints.AssertTrue;

@Data
public class QuoteRequestDto {
    private String requestType; // "PRINT_SERVICE" or "DESIGN_SERVICE"
    private String customerType; // "PRIVATE" or "BUSINESS"
    private String language; // "it" | "en" | "de" | "fr"
    private String email;
    private String phone;
    private String name;
    private String companyName;
    private String contactPerson;
    private String message;

    @AssertTrue(message = "L'accettazione dei Termini e Condizioni e obbligatoria.")
    private boolean acceptTerms;

    @AssertTrue(message = "L'accettazione dell'Informativa Privacy e obbligatoria.")
    private boolean acceptPrivacy;
}

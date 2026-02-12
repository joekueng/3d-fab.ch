package com.printcalculator.dto;

import lombok.Data;

@Data
public class QuoteRequestDto {
    private String requestType; // "PRINT_SERVICE" or "DESIGN_SERVICE"
    private String customerType; // "PRIVATE" or "BUSINESS"
    private String email;
    private String phone;
    private String name;
    private String companyName;
    private String contactPerson;
    private String message;
}

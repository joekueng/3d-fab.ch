package com.printcalculator.dto;

import lombok.Data;

@Data
public class CustomerDto {
    private String email;
    private String phone;
    private String customerType; // "PRIVATE", "BUSINESS"
}

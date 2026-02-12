package com.printcalculator.dto;

import lombok.Data;

@Data
public class AddressDto {
    private String firstName;
    private String lastName;
    private String companyName;
    private String contactPerson;
    private String addressLine1;
    private String addressLine2;
    private String zip;
    private String city;
    private String countryCode;
}

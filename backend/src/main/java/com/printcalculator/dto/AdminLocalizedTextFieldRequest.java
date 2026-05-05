package com.printcalculator.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class AdminLocalizedTextFieldRequest {

    private Boolean required;

    @NotNull
    private Map<String, String> values;

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }
}

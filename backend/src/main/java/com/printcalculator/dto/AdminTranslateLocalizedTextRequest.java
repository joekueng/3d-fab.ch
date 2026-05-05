package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public class AdminTranslateLocalizedTextRequest {

    @NotBlank
    private String context;

    @NotBlank
    private String sourceLanguage;

    private Boolean overwriteExisting;

    @Valid
    @NotEmpty
    private Map<String, AdminLocalizedTextFieldRequest> fields;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public Boolean getOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(Boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public Map<String, AdminLocalizedTextFieldRequest> getFields() {
        return fields;
    }

    public void setFields(Map<String, AdminLocalizedTextFieldRequest> fields) {
        this.fields = fields;
    }
}

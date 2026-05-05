package com.printcalculator.dto;

import java.util.List;
import java.util.Map;

public class AdminTranslateLocalizedTextResponse {

    private String sourceLanguage;
    private List<String> targetLanguages;
    private Map<String, Map<String, String>> fields;

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public List<String> getTargetLanguages() {
        return targetLanguages;
    }

    public void setTargetLanguages(List<String> targetLanguages) {
        this.targetLanguages = targetLanguages;
    }

    public Map<String, Map<String, String>> getFields() {
        return fields;
    }

    public void setFields(Map<String, Map<String, String>> fields) {
        this.fields = fields;
    }
}

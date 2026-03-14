package com.printcalculator.dto;

import java.util.List;
import java.util.Map;

public class AdminTranslateShopProductResponse {
    private String sourceLanguage;
    private List<String> targetLanguages;
    private Map<String, String> names;
    private Map<String, String> excerpts;
    private Map<String, String> descriptions;
    private Map<String, String> seoTitles;
    private Map<String, String> seoDescriptions;

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

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public Map<String, String> getExcerpts() {
        return excerpts;
    }

    public void setExcerpts(Map<String, String> excerpts) {
        this.excerpts = excerpts;
    }

    public Map<String, String> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(Map<String, String> descriptions) {
        this.descriptions = descriptions;
    }

    public Map<String, String> getSeoTitles() {
        return seoTitles;
    }

    public void setSeoTitles(Map<String, String> seoTitles) {
        this.seoTitles = seoTitles;
    }

    public Map<String, String> getSeoDescriptions() {
        return seoDescriptions;
    }

    public void setSeoDescriptions(Map<String, String> seoDescriptions) {
        this.seoDescriptions = seoDescriptions;
    }
}

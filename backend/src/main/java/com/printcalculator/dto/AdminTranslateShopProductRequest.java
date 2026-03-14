package com.printcalculator.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminTranslateShopProductRequest {
    private UUID categoryId;
    private String sourceLanguage;
    private Boolean overwriteExisting;
    private List<String> materialCodes;
    private Map<String, String> names;
    private Map<String, String> excerpts;
    private Map<String, String> descriptions;
    private Map<String, String> seoTitles;
    private Map<String, String> seoDescriptions;

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
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

    public List<String> getMaterialCodes() {
        return materialCodes;
    }

    public void setMaterialCodes(List<String> materialCodes) {
        this.materialCodes = materialCodes;
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

package com.printcalculator.dto;

import java.util.UUID;
import java.util.Map;

public class AdminUpdateMediaUsageRequest {
    private String usageType;
    private String usageKey;
    private UUID ownerId;
    private UUID mediaAssetId;
    private Integer sortOrder;
    private Boolean isPrimary;
    private Boolean isActive;
    private Map<String, MediaTextTranslationDto> translations;

    public String getUsageType() {
        return usageType;
    }

    public void setUsageType(String usageType) {
        this.usageType = usageType;
    }

    public String getUsageKey() {
        return usageKey;
    }

    public void setUsageKey(String usageKey) {
        this.usageKey = usageKey;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getMediaAssetId() {
        return mediaAssetId;
    }

    public void setMediaAssetId(UUID mediaAssetId) {
        this.mediaAssetId = mediaAssetId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean primary) {
        isPrimary = primary;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Map<String, MediaTextTranslationDto> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, MediaTextTranslationDto> translations) {
        this.translations = translations;
    }
}

package com.printcalculator.dto;

import java.util.UUID;

public class PublicMediaUsageDto {
    private UUID mediaAssetId;
    private String title;
    private String altText;
    private String usageType;
    private String usageKey;
    private Integer sortOrder;
    private Boolean isPrimary;
    private PublicMediaVariantDto thumb;
    private PublicMediaVariantDto card;
    private PublicMediaVariantDto hero;

    public UUID getMediaAssetId() {
        return mediaAssetId;
    }

    public void setMediaAssetId(UUID mediaAssetId) {
        this.mediaAssetId = mediaAssetId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

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

    public PublicMediaVariantDto getThumb() {
        return thumb;
    }

    public void setThumb(PublicMediaVariantDto thumb) {
        this.thumb = thumb;
    }

    public PublicMediaVariantDto getCard() {
        return card;
    }

    public void setCard(PublicMediaVariantDto card) {
        this.card = card;
    }

    public PublicMediaVariantDto getHero() {
        return hero;
    }

    public void setHero(PublicMediaVariantDto hero) {
        this.hero = hero;
    }
}

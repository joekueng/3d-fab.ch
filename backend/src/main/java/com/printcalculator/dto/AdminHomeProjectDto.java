package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AdminHomeProjectDto {
    private UUID id;
    private String slug;
    private String eyebrowIt;
    private String eyebrowEn;
    private String eyebrowDe;
    private String eyebrowFr;
    private String titleIt;
    private String titleEn;
    private String titleDe;
    private String titleFr;
    private String descriptionIt;
    private String descriptionEn;
    private String descriptionDe;
    private String descriptionFr;
    private Boolean isActive;
    private Integer sortOrder;
    private String mediaUsageType;
    private String mediaUsageKey;
    private List<AdminMediaUsageDto> mediaUsages;
    private List<PublicMediaUsageDto> images;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getEyebrowIt() {
        return eyebrowIt;
    }

    public void setEyebrowIt(String eyebrowIt) {
        this.eyebrowIt = eyebrowIt;
    }

    public String getEyebrowEn() {
        return eyebrowEn;
    }

    public void setEyebrowEn(String eyebrowEn) {
        this.eyebrowEn = eyebrowEn;
    }

    public String getEyebrowDe() {
        return eyebrowDe;
    }

    public void setEyebrowDe(String eyebrowDe) {
        this.eyebrowDe = eyebrowDe;
    }

    public String getEyebrowFr() {
        return eyebrowFr;
    }

    public void setEyebrowFr(String eyebrowFr) {
        this.eyebrowFr = eyebrowFr;
    }

    public String getTitleIt() {
        return titleIt;
    }

    public void setTitleIt(String titleIt) {
        this.titleIt = titleIt;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleDe() {
        return titleDe;
    }

    public void setTitleDe(String titleDe) {
        this.titleDe = titleDe;
    }

    public String getTitleFr() {
        return titleFr;
    }

    public void setTitleFr(String titleFr) {
        this.titleFr = titleFr;
    }

    public String getDescriptionIt() {
        return descriptionIt;
    }

    public void setDescriptionIt(String descriptionIt) {
        this.descriptionIt = descriptionIt;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getDescriptionDe() {
        return descriptionDe;
    }

    public void setDescriptionDe(String descriptionDe) {
        this.descriptionDe = descriptionDe;
    }

    public String getDescriptionFr() {
        return descriptionFr;
    }

    public void setDescriptionFr(String descriptionFr) {
        this.descriptionFr = descriptionFr;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getMediaUsageType() {
        return mediaUsageType;
    }

    public void setMediaUsageType(String mediaUsageType) {
        this.mediaUsageType = mediaUsageType;
    }

    public String getMediaUsageKey() {
        return mediaUsageKey;
    }

    public void setMediaUsageKey(String mediaUsageKey) {
        this.mediaUsageKey = mediaUsageKey;
    }

    public List<AdminMediaUsageDto> getMediaUsages() {
        return mediaUsages;
    }

    public void setMediaUsages(List<AdminMediaUsageDto> mediaUsages) {
        this.mediaUsages = mediaUsages;
    }

    public List<PublicMediaUsageDto> getImages() {
        return images;
    }

    public void setImages(List<PublicMediaUsageDto> images) {
        this.images = images;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

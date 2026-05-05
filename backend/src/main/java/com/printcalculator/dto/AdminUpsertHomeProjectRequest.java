package com.printcalculator.dto;

public class AdminUpsertHomeProjectRequest {
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
}

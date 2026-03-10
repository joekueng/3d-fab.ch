package com.printcalculator.dto;

import java.util.List;
import java.util.UUID;

public class AdminUpsertShopProductRequest {
    private UUID categoryId;
    private String slug;
    private String name;
    private String nameIt;
    private String nameEn;
    private String nameDe;
    private String nameFr;
    private String excerpt;
    private String excerptIt;
    private String excerptEn;
    private String excerptDe;
    private String excerptFr;
    private String description;
    private String descriptionIt;
    private String descriptionEn;
    private String descriptionDe;
    private String descriptionFr;
    private String seoTitle;
    private String seoTitleIt;
    private String seoTitleEn;
    private String seoTitleDe;
    private String seoTitleFr;
    private String seoDescription;
    private String seoDescriptionIt;
    private String seoDescriptionEn;
    private String seoDescriptionDe;
    private String seoDescriptionFr;
    private String ogTitle;
    private String ogDescription;
    private Boolean indexable;
    private Boolean isFeatured;
    private Boolean isActive;
    private Integer sortOrder;
    private List<AdminUpsertShopProductVariantRequest> variants;

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameIt() {
        return nameIt;
    }

    public void setNameIt(String nameIt) {
        this.nameIt = nameIt;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameDe() {
        return nameDe;
    }

    public void setNameDe(String nameDe) {
        this.nameDe = nameDe;
    }

    public String getNameFr() {
        return nameFr;
    }

    public void setNameFr(String nameFr) {
        this.nameFr = nameFr;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public String getExcerptIt() {
        return excerptIt;
    }

    public void setExcerptIt(String excerptIt) {
        this.excerptIt = excerptIt;
    }

    public String getExcerptEn() {
        return excerptEn;
    }

    public void setExcerptEn(String excerptEn) {
        this.excerptEn = excerptEn;
    }

    public String getExcerptDe() {
        return excerptDe;
    }

    public void setExcerptDe(String excerptDe) {
        this.excerptDe = excerptDe;
    }

    public String getExcerptFr() {
        return excerptFr;
    }

    public void setExcerptFr(String excerptFr) {
        this.excerptFr = excerptFr;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getSeoTitle() {
        return seoTitle;
    }

    public void setSeoTitle(String seoTitle) {
        this.seoTitle = seoTitle;
    }

    public String getSeoTitleIt() {
        return seoTitleIt;
    }

    public void setSeoTitleIt(String seoTitleIt) {
        this.seoTitleIt = seoTitleIt;
    }

    public String getSeoTitleEn() {
        return seoTitleEn;
    }

    public void setSeoTitleEn(String seoTitleEn) {
        this.seoTitleEn = seoTitleEn;
    }

    public String getSeoTitleDe() {
        return seoTitleDe;
    }

    public void setSeoTitleDe(String seoTitleDe) {
        this.seoTitleDe = seoTitleDe;
    }

    public String getSeoTitleFr() {
        return seoTitleFr;
    }

    public void setSeoTitleFr(String seoTitleFr) {
        this.seoTitleFr = seoTitleFr;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(String seoDescription) {
        this.seoDescription = seoDescription;
    }

    public String getSeoDescriptionIt() {
        return seoDescriptionIt;
    }

    public void setSeoDescriptionIt(String seoDescriptionIt) {
        this.seoDescriptionIt = seoDescriptionIt;
    }

    public String getSeoDescriptionEn() {
        return seoDescriptionEn;
    }

    public void setSeoDescriptionEn(String seoDescriptionEn) {
        this.seoDescriptionEn = seoDescriptionEn;
    }

    public String getSeoDescriptionDe() {
        return seoDescriptionDe;
    }

    public void setSeoDescriptionDe(String seoDescriptionDe) {
        this.seoDescriptionDe = seoDescriptionDe;
    }

    public String getSeoDescriptionFr() {
        return seoDescriptionFr;
    }

    public void setSeoDescriptionFr(String seoDescriptionFr) {
        this.seoDescriptionFr = seoDescriptionFr;
    }

    public String getOgTitle() {
        return ogTitle;
    }

    public void setOgTitle(String ogTitle) {
        this.ogTitle = ogTitle;
    }

    public String getOgDescription() {
        return ogDescription;
    }

    public void setOgDescription(String ogDescription) {
        this.ogDescription = ogDescription;
    }

    public Boolean getIndexable() {
        return indexable;
    }

    public void setIndexable(Boolean indexable) {
        this.indexable = indexable;
    }

    public Boolean getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Boolean featured) {
        isFeatured = featured;
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

    public List<AdminUpsertShopProductVariantRequest> getVariants() {
        return variants;
    }

    public void setVariants(List<AdminUpsertShopProductVariantRequest> variants) {
        this.variants = variants;
    }
}

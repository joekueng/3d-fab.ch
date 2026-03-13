package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AdminShopCategoryDto {
    private UUID id;
    private UUID parentCategoryId;
    private String parentCategoryName;
    private String slug;
    private String name;
    private String nameIt;
    private String nameEn;
    private String nameDe;
    private String nameFr;
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
    private Boolean isActive;
    private Integer sortOrder;
    private Integer depth;
    private Integer childCount;
    private Integer directProductCount;
    private Integer descendantProductCount;
    private String mediaUsageType;
    private String mediaUsageKey;
    private List<AdminShopCategoryRefDto> breadcrumbs;
    private List<AdminShopCategoryDto> children;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(UUID parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
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

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getChildCount() {
        return childCount;
    }

    public void setChildCount(Integer childCount) {
        this.childCount = childCount;
    }

    public Integer getDirectProductCount() {
        return directProductCount;
    }

    public void setDirectProductCount(Integer directProductCount) {
        this.directProductCount = directProductCount;
    }

    public Integer getDescendantProductCount() {
        return descendantProductCount;
    }

    public void setDescendantProductCount(Integer descendantProductCount) {
        this.descendantProductCount = descendantProductCount;
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

    public List<AdminShopCategoryRefDto> getBreadcrumbs() {
        return breadcrumbs;
    }

    public void setBreadcrumbs(List<AdminShopCategoryRefDto> breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
    }

    public List<AdminShopCategoryDto> getChildren() {
        return children;
    }

    public void setChildren(List<AdminShopCategoryDto> children) {
        this.children = children;
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

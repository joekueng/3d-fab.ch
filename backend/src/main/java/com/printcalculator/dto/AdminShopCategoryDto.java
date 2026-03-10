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
    private String description;
    private String seoTitle;
    private String seoDescription;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeoTitle() {
        return seoTitle;
    }

    public void setSeoTitle(String seoTitle) {
        this.seoTitle = seoTitle;
    }

    public String getSeoDescription() {
        return seoDescription;
    }

    public void setSeoDescription(String seoDescription) {
        this.seoDescription = seoDescription;
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

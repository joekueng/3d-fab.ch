package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AdminShopProductDto {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String categorySlug;
    private String slug;
    private String name;
    private String excerpt;
    private String description;
    private String seoTitle;
    private String seoDescription;
    private String ogTitle;
    private String ogDescription;
    private Boolean indexable;
    private Boolean isFeatured;
    private Boolean isActive;
    private Integer sortOrder;
    private Integer variantCount;
    private Integer activeVariantCount;
    private BigDecimal priceFromChf;
    private BigDecimal priceToChf;
    private String mediaUsageType;
    private String mediaUsageKey;
    private List<AdminMediaUsageDto> mediaUsages;
    private List<PublicMediaUsageDto> images;
    private ShopProductModelDto model3d;
    private List<AdminShopProductVariantDto> variants;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategorySlug() {
        return categorySlug;
    }

    public void setCategorySlug(String categorySlug) {
        this.categorySlug = categorySlug;
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

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
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

    public Integer getVariantCount() {
        return variantCount;
    }

    public void setVariantCount(Integer variantCount) {
        this.variantCount = variantCount;
    }

    public Integer getActiveVariantCount() {
        return activeVariantCount;
    }

    public void setActiveVariantCount(Integer activeVariantCount) {
        this.activeVariantCount = activeVariantCount;
    }

    public BigDecimal getPriceFromChf() {
        return priceFromChf;
    }

    public void setPriceFromChf(BigDecimal priceFromChf) {
        this.priceFromChf = priceFromChf;
    }

    public BigDecimal getPriceToChf() {
        return priceToChf;
    }

    public void setPriceToChf(BigDecimal priceToChf) {
        this.priceToChf = priceToChf;
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

    public ShopProductModelDto getModel3d() {
        return model3d;
    }

    public void setModel3d(ShopProductModelDto model3d) {
        this.model3d = model3d;
    }

    public List<AdminShopProductVariantDto> getVariants() {
        return variants;
    }

    public void setVariants(List<AdminShopProductVariantDto> variants) {
        this.variants = variants;
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

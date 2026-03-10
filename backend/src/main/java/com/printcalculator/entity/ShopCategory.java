package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shop_category", indexes = {
        @Index(name = "ix_shop_category_parent_sort", columnList = "parent_category_id, sort_order"),
        @Index(name = "ix_shop_category_active_sort", columnList = "is_active, sort_order")
})
public class ShopCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "shop_category_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private ShopCategory parentCategory;

    @Column(name = "slug", nullable = false, unique = true, length = Integer.MAX_VALUE)
    private String slug;

    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "seo_title", length = Integer.MAX_VALUE)
    private String seoTitle;

    @Column(name = "seo_description", length = Integer.MAX_VALUE)
    private String seoDescription;

    @Column(name = "og_title", length = Integer.MAX_VALUE)
    private String ogTitle;

    @Column(name = "og_description", length = Integer.MAX_VALUE)
    private String ogDescription;

    @ColumnDefault("true")
    @Column(name = "indexable", nullable = false)
    private Boolean indexable;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (indexable == null) {
            indexable = true;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (indexable == null) {
            indexable = true;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ShopCategory getParentCategory() {
        return parentCategory;
    }

    public void setParentCategory(ShopCategory parentCategory) {
        this.parentCategory = parentCategory;
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

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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shop_product", indexes = {
        @Index(name = "ix_shop_product_category_active_sort", columnList = "shop_category_id, is_active, sort_order"),
        @Index(name = "ix_shop_product_featured_sort", columnList = "is_featured, is_active, sort_order")
})
public class ShopProduct {
    public static final List<String> SUPPORTED_LANGUAGES = List.of("it", "en", "de", "fr");

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "shop_product_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_category_id", nullable = false)
    private ShopCategory category;

    @Column(name = "slug", nullable = false, unique = true, length = Integer.MAX_VALUE)
    private String slug;

    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "name_it", length = Integer.MAX_VALUE)
    private String nameIt;

    @Column(name = "name_en", length = Integer.MAX_VALUE)
    private String nameEn;

    @Column(name = "name_de", length = Integer.MAX_VALUE)
    private String nameDe;

    @Column(name = "name_fr", length = Integer.MAX_VALUE)
    private String nameFr;

    @Column(name = "excerpt", length = Integer.MAX_VALUE)
    private String excerpt;

    @Column(name = "excerpt_it", length = Integer.MAX_VALUE)
    private String excerptIt;

    @Column(name = "excerpt_en", length = Integer.MAX_VALUE)
    private String excerptEn;

    @Column(name = "excerpt_de", length = Integer.MAX_VALUE)
    private String excerptDe;

    @Column(name = "excerpt_fr", length = Integer.MAX_VALUE)
    private String excerptFr;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "description_it", length = Integer.MAX_VALUE)
    private String descriptionIt;

    @Column(name = "description_en", length = Integer.MAX_VALUE)
    private String descriptionEn;

    @Column(name = "description_de", length = Integer.MAX_VALUE)
    private String descriptionDe;

    @Column(name = "description_fr", length = Integer.MAX_VALUE)
    private String descriptionFr;

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

    @ColumnDefault("false")
    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

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
        if (isFeatured == null) {
            isFeatured = false;
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
        if (isFeatured == null) {
            isFeatured = false;
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

    public ShopCategory getCategory() {
        return category;
    }

    public void setCategory(ShopCategory category) {
        this.category = category;
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

    public String getNameForLanguage(String language) {
        return resolveLocalizedValue(language, name, nameIt, nameEn, nameDe, nameFr);
    }

    public void setNameForLanguage(String language, String value) {
        switch (normalizeLanguage(language)) {
            case "it" -> nameIt = value;
            case "en" -> nameEn = value;
            case "de" -> nameDe = value;
            case "fr" -> nameFr = value;
            default -> {
            }
        }
    }

    public String getExcerptForLanguage(String language) {
        return resolveLocalizedValue(language, excerpt, excerptIt, excerptEn, excerptDe, excerptFr);
    }

    public void setExcerptForLanguage(String language, String value) {
        switch (normalizeLanguage(language)) {
            case "it" -> excerptIt = value;
            case "en" -> excerptEn = value;
            case "de" -> excerptDe = value;
            case "fr" -> excerptFr = value;
            default -> {
            }
        }
    }

    public String getDescriptionForLanguage(String language) {
        return resolveLocalizedValue(language, description, descriptionIt, descriptionEn, descriptionDe, descriptionFr);
    }

    public void setDescriptionForLanguage(String language, String value) {
        switch (normalizeLanguage(language)) {
            case "it" -> descriptionIt = value;
            case "en" -> descriptionEn = value;
            case "de" -> descriptionDe = value;
            case "fr" -> descriptionFr = value;
            default -> {
            }
        }
    }

    private String resolveLocalizedValue(String language,
                                         String fallback,
                                         String valueIt,
                                         String valueEn,
                                         String valueDe,
                                         String valueFr) {
        String normalizedLanguage = normalizeLanguage(language);
        String preferred = switch (normalizedLanguage) {
            case "it" -> valueIt;
            case "en" -> valueEn;
            case "de" -> valueDe;
            case "fr" -> valueFr;
            default -> null;
        };
        String resolved = firstNonBlank(preferred, fallback);
        if (resolved != null) {
            return resolved;
        }
        return firstNonBlank(valueIt, valueEn, valueDe, valueFr);
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String normalized = language.trim().toLowerCase();
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex > 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

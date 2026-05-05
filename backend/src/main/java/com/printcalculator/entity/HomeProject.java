package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "home_project", indexes = {
        @Index(name = "ix_home_project_active_sort", columnList = "is_active, sort_order")
})
public class HomeProject {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "home_project_id", nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = Integer.MAX_VALUE)
    private String slug;

    @Column(name = "eyebrow_it", length = Integer.MAX_VALUE)
    private String eyebrowIt;

    @Column(name = "eyebrow_en", length = Integer.MAX_VALUE)
    private String eyebrowEn;

    @Column(name = "eyebrow_de", length = Integer.MAX_VALUE)
    private String eyebrowDe;

    @Column(name = "eyebrow_fr", length = Integer.MAX_VALUE)
    private String eyebrowFr;

    @Column(name = "title_it", length = Integer.MAX_VALUE)
    private String titleIt;

    @Column(name = "title_en", length = Integer.MAX_VALUE)
    private String titleEn;

    @Column(name = "title_de", length = Integer.MAX_VALUE)
    private String titleDe;

    @Column(name = "title_fr", length = Integer.MAX_VALUE)
    private String titleFr;

    @Column(name = "description_it", length = Integer.MAX_VALUE)
    private String descriptionIt;

    @Column(name = "description_en", length = Integer.MAX_VALUE)
    private String descriptionEn;

    @Column(name = "description_de", length = Integer.MAX_VALUE)
    private String descriptionDe;

    @Column(name = "description_fr", length = Integer.MAX_VALUE)
    private String descriptionFr;

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
        applyDefaults();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
        applyDefaults();
    }

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

    public String getEyebrowForLanguage(String language) {
        return resolveLocalizedValue(language, eyebrowIt, eyebrowEn, eyebrowDe, eyebrowFr);
    }

    public String getTitleForLanguage(String language) {
        return resolveLocalizedValue(language, titleIt, titleEn, titleDe, titleFr);
    }

    public String getDescriptionForLanguage(String language) {
        return resolveLocalizedValue(language, descriptionIt, descriptionEn, descriptionDe, descriptionFr);
    }

    private void applyDefaults() {
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    private String resolveLocalizedValue(String language,
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
        return firstNonBlank(preferred, valueIt, valueEn, valueDe, valueFr);
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
                return value.trim();
            }
        }
        return null;
    }
}

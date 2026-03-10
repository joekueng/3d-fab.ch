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
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_usage", indexes = {
        @Index(name = "ix_media_usage_scope", columnList = "usage_type, usage_key, is_active, sort_order")
})
public class MediaUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "media_usage_id", nullable = false)
    private UUID id;

    @Column(name = "usage_type", nullable = false, length = Integer.MAX_VALUE)
    private String usageType;

    @Column(name = "usage_key", nullable = false, length = Integer.MAX_VALUE)
    private String usageKey;

    @Column(name = "owner_id")
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset mediaAsset;

    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @ColumnDefault("false")
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "title_it", length = Integer.MAX_VALUE)
    private String titleIt;

    @Column(name = "title_en", length = Integer.MAX_VALUE)
    private String titleEn;

    @Column(name = "title_de", length = Integer.MAX_VALUE)
    private String titleDe;

    @Column(name = "title_fr", length = Integer.MAX_VALUE)
    private String titleFr;

    @Column(name = "alt_text_it", length = Integer.MAX_VALUE)
    private String altTextIt;

    @Column(name = "alt_text_en", length = Integer.MAX_VALUE)
    private String altTextEn;

    @Column(name = "alt_text_de", length = Integer.MAX_VALUE)
    private String altTextDe;

    @Column(name = "alt_text_fr", length = Integer.MAX_VALUE)
    private String altTextFr;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public MediaAsset getMediaAsset() {
        return mediaAsset;
    }

    public void setMediaAsset(MediaAsset mediaAsset) {
        this.mediaAsset = mediaAsset;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
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

    public String getAltTextIt() {
        return altTextIt;
    }

    public void setAltTextIt(String altTextIt) {
        this.altTextIt = altTextIt;
    }

    public String getAltTextEn() {
        return altTextEn;
    }

    public void setAltTextEn(String altTextEn) {
        this.altTextEn = altTextEn;
    }

    public String getAltTextDe() {
        return altTextDe;
    }

    public void setAltTextDe(String altTextDe) {
        this.altTextDe = altTextDe;
    }

    public String getAltTextFr() {
        return altTextFr;
    }

    public void setAltTextFr(String altTextFr) {
        this.altTextFr = altTextFr;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitleForLanguage(String language) {
        if (language == null) {
            return null;
        }
        return switch (language.trim().toLowerCase()) {
            case "it" -> titleIt;
            case "en" -> titleEn;
            case "de" -> titleDe;
            case "fr" -> titleFr;
            default -> null;
        };
    }

    public void setTitleForLanguage(String language, String value) {
        if (language == null) {
            return;
        }
        switch (language.trim().toLowerCase()) {
            case "it" -> titleIt = value;
            case "en" -> titleEn = value;
            case "de" -> titleDe = value;
            case "fr" -> titleFr = value;
            default -> {
            }
        }
    }

    public String getAltTextForLanguage(String language) {
        if (language == null) {
            return null;
        }
        return switch (language.trim().toLowerCase()) {
            case "it" -> altTextIt;
            case "en" -> altTextEn;
            case "de" -> altTextDe;
            case "fr" -> altTextFr;
            default -> null;
        };
    }

    public void setAltTextForLanguage(String language, String value) {
        if (language == null) {
            return;
        }
        switch (language.trim().toLowerCase()) {
            case "it" -> altTextIt = value;
            case "en" -> altTextEn = value;
            case "de" -> altTextDe = value;
            case "fr" -> altTextFr = value;
            default -> {
            }
        }
    }
}

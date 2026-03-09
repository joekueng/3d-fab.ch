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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_variant", indexes = {
        @Index(name = "ix_media_variant_asset", columnList = "media_asset_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_media_variant_asset_name_format", columnNames = {"media_asset_id", "variant_name", "format"})
})
public class MediaVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "media_variant_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset mediaAsset;

    @Column(name = "variant_name", nullable = false, length = Integer.MAX_VALUE)
    private String variantName;

    @Column(name = "format", nullable = false, length = Integer.MAX_VALUE)
    private String format;

    @Column(name = "storage_key", nullable = false, length = Integer.MAX_VALUE, unique = true)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = Integer.MAX_VALUE)
    private String mimeType;

    @Column(name = "width_px", nullable = false)
    private Integer widthPx;

    @Column(name = "height_px", nullable = false)
    private Integer heightPx;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @ColumnDefault("true")
    @Column(name = "is_generated", nullable = false)
    private Boolean isGenerated;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public MediaAsset getMediaAsset() {
        return mediaAsset;
    }

    public void setMediaAsset(MediaAsset mediaAsset) {
        this.mediaAsset = mediaAsset;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(Integer heightPx) {
        this.heightPx = heightPx;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Boolean getIsGenerated() {
        return isGenerated;
    }

    public void setIsGenerated(Boolean generated) {
        isGenerated = generated;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

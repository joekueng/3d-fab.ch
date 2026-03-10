package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminMediaVariantDto {
    private UUID id;
    private String variantName;
    private String format;
    private String storageKey;
    private String mimeType;
    private Integer widthPx;
    private Integer heightPx;
    private Long fileSizeBytes;
    private Boolean isGenerated;
    private String publicUrl;
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

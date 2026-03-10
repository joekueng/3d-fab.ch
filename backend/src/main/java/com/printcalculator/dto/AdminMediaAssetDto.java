package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminMediaAssetDto {
    private UUID id;
    private String originalFilename;
    private String storageKey;
    private String mimeType;
    private Long fileSizeBytes;
    private String sha256Hex;
    private Integer widthPx;
    private Integer heightPx;
    private String status;
    private String visibility;
    private String title;
    private String altText;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<AdminMediaVariantDto> variants = new ArrayList<>();
    private List<AdminMediaUsageDto> usages = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
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

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
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

    public List<AdminMediaVariantDto> getVariants() {
        return variants;
    }

    public void setVariants(List<AdminMediaVariantDto> variants) {
        this.variants = variants;
    }

    public List<AdminMediaUsageDto> getUsages() {
        return usages;
    }

    public void setUsages(List<AdminMediaUsageDto> usages) {
        this.usages = usages;
    }
}

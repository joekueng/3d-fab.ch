package com.printcalculator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AdminQrOverviewItemDto {
    private UUID qrLinkId;
    private String name;
    private String slug;
    private String targetPath;
    private Boolean isActive;
    private String publicUrl;
    private long rawScans;
    private long uniqueVisitors;
    private String topLocationLabel;
    private long topLocationScans;
    private OffsetDateTime lastScannedAt;

    public UUID getQrLinkId() {
        return qrLinkId;
    }

    public void setQrLinkId(UUID qrLinkId) {
        this.qrLinkId = qrLinkId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public long getRawScans() {
        return rawScans;
    }

    public void setRawScans(long rawScans) {
        this.rawScans = rawScans;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public void setUniqueVisitors(long uniqueVisitors) {
        this.uniqueVisitors = uniqueVisitors;
    }

    public String getTopLocationLabel() {
        return topLocationLabel;
    }

    public void setTopLocationLabel(String topLocationLabel) {
        this.topLocationLabel = topLocationLabel;
    }

    public long getTopLocationScans() {
        return topLocationScans;
    }

    public void setTopLocationScans(long topLocationScans) {
        this.topLocationScans = topLocationScans;
    }

    public OffsetDateTime getLastScannedAt() {
        return lastScannedAt;
    }

    public void setLastScannedAt(OffsetDateTime lastScannedAt) {
        this.lastScannedAt = lastScannedAt;
    }
}

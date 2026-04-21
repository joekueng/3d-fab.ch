package com.printcalculator.dto;

import java.util.UUID;

public class AdminQrDailyBreakdownDto {
    private UUID qrLinkId;
    private String name;
    private String slug;
    private long scans;

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

    public long getScans() {
        return scans;
    }

    public void setScans(long scans) {
        this.scans = scans;
    }
}

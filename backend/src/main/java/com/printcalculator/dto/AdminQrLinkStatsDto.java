package com.printcalculator.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AdminQrLinkStatsDto {
    private UUID qrLinkId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private long rawScans;
    private long uniqueVisitors;
    private long excludedBotScans;
    private OffsetDateTime lastScannedAt;
    private List<AdminQrDailyStatDto> daily;
    private List<AdminQrLanguageStatDto> languages;
    private List<AdminQrLocationStatDto> locations;
    private List<AdminQrScanEventDto> recentScans;

    public UUID getQrLinkId() {
        return qrLinkId;
    }

    public void setQrLinkId(UUID qrLinkId) {
        this.qrLinkId = qrLinkId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
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

    public long getExcludedBotScans() {
        return excludedBotScans;
    }

    public void setExcludedBotScans(long excludedBotScans) {
        this.excludedBotScans = excludedBotScans;
    }

    public OffsetDateTime getLastScannedAt() {
        return lastScannedAt;
    }

    public void setLastScannedAt(OffsetDateTime lastScannedAt) {
        this.lastScannedAt = lastScannedAt;
    }

    public List<AdminQrDailyStatDto> getDaily() {
        return daily;
    }

    public void setDaily(List<AdminQrDailyStatDto> daily) {
        this.daily = daily;
    }

    public List<AdminQrLanguageStatDto> getLanguages() {
        return languages;
    }

    public void setLanguages(List<AdminQrLanguageStatDto> languages) {
        this.languages = languages;
    }

    public List<AdminQrLocationStatDto> getLocations() {
        return locations;
    }

    public void setLocations(List<AdminQrLocationStatDto> locations) {
        this.locations = locations;
    }

    public List<AdminQrScanEventDto> getRecentScans() {
        return recentScans;
    }

    public void setRecentScans(List<AdminQrScanEventDto> recentScans) {
        this.recentScans = recentScans;
    }
}

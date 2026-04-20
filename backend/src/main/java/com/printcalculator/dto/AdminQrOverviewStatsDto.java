package com.printcalculator.dto;

import java.time.LocalDate;
import java.util.List;

public class AdminQrOverviewStatsDto {
    private LocalDate fromDate;
    private LocalDate toDate;
    private int totalQrLinks;
    private int activeQrLinks;
    private long rawScans;
    private long uniqueVisitors;
    private List<AdminQrDailyStatDto> daily;
    private List<AdminQrLocationStatDto> locations;
    private List<AdminQrOverviewItemDto> qrLinks;

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

    public int getTotalQrLinks() {
        return totalQrLinks;
    }

    public void setTotalQrLinks(int totalQrLinks) {
        this.totalQrLinks = totalQrLinks;
    }

    public int getActiveQrLinks() {
        return activeQrLinks;
    }

    public void setActiveQrLinks(int activeQrLinks) {
        this.activeQrLinks = activeQrLinks;
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

    public List<AdminQrDailyStatDto> getDaily() {
        return daily;
    }

    public void setDaily(List<AdminQrDailyStatDto> daily) {
        this.daily = daily;
    }

    public List<AdminQrLocationStatDto> getLocations() {
        return locations;
    }

    public void setLocations(List<AdminQrLocationStatDto> locations) {
        this.locations = locations;
    }

    public List<AdminQrOverviewItemDto> getQrLinks() {
        return qrLinks;
    }

    public void setQrLinks(List<AdminQrOverviewItemDto> qrLinks) {
        this.qrLinks = qrLinks;
    }
}

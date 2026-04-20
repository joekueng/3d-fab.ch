package com.printcalculator.dto;

import java.time.LocalDate;

public class AdminQrDailyStatDto {
    private LocalDate date;
    private long scans;
    private long uniqueVisitors;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getScans() {
        return scans;
    }

    public void setScans(long scans) {
        this.scans = scans;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public void setUniqueVisitors(long uniqueVisitors) {
        this.uniqueVisitors = uniqueVisitors;
    }
}

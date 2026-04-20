package com.printcalculator.dto;

import java.time.OffsetDateTime;

public class AdminQrScanEventDto {
    private OffsetDateTime scannedAt;
    private String resolvedLang;
    private String finalPath;
    private String countryCode;
    private String countryName;
    private String cityName;

    public OffsetDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(OffsetDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }

    public String getResolvedLang() {
        return resolvedLang;
    }

    public void setResolvedLang(String resolvedLang) {
        this.resolvedLang = resolvedLang;
    }

    public String getFinalPath() {
        return finalPath;
    }

    public void setFinalPath(String finalPath) {
        this.finalPath = finalPath;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }
}

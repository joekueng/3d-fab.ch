package com.printcalculator.dto;

public class AdminQrLocationStatDto {
    private String countryCode;
    private String countryName;
    private String cityName;
    private String label;
    private long scans;

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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getScans() {
        return scans;
    }

    public void setScans(long scans) {
        this.scans = scans;
    }
}

package com.printcalculator.dto;

public class PublicMediaVariantDto {
    private String avifUrl;
    private String webpUrl;
    private String jpegUrl;
    private String pngUrl;

    public String getAvifUrl() {
        return avifUrl;
    }

    public void setAvifUrl(String avifUrl) {
        this.avifUrl = avifUrl;
    }

    public String getWebpUrl() {
        return webpUrl;
    }

    public void setWebpUrl(String webpUrl) {
        this.webpUrl = webpUrl;
    }

    public String getJpegUrl() {
        return jpegUrl;
    }

    public void setJpegUrl(String jpegUrl) {
        this.jpegUrl = jpegUrl;
    }

    public String getPngUrl() {
        return pngUrl;
    }

    public void setPngUrl(String pngUrl) {
        this.pngUrl = pngUrl;
    }
}

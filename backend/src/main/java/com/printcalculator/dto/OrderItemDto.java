package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItemDto {
    private UUID id;
    private String originalFilename;
    private String materialCode;
    private String colorCode;
    private String quality;
    private BigDecimal nozzleDiameterMm;
    private BigDecimal layerHeightMm;
    private Integer infillPercent;
    private String infillPattern;
    private Boolean supportsEnabled;
    private Integer quantity;
    private Integer printTimeSeconds;
    private BigDecimal materialGrams;
    private BigDecimal unitPriceChf;
    private BigDecimal lineTotalChf;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }

    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }

    public BigDecimal getNozzleDiameterMm() { return nozzleDiameterMm; }
    public void setNozzleDiameterMm(BigDecimal nozzleDiameterMm) { this.nozzleDiameterMm = nozzleDiameterMm; }

    public BigDecimal getLayerHeightMm() { return layerHeightMm; }
    public void setLayerHeightMm(BigDecimal layerHeightMm) { this.layerHeightMm = layerHeightMm; }

    public Integer getInfillPercent() { return infillPercent; }
    public void setInfillPercent(Integer infillPercent) { this.infillPercent = infillPercent; }

    public String getInfillPattern() { return infillPattern; }
    public void setInfillPattern(String infillPattern) { this.infillPattern = infillPattern; }

    public Boolean getSupportsEnabled() { return supportsEnabled; }
    public void setSupportsEnabled(Boolean supportsEnabled) { this.supportsEnabled = supportsEnabled; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getPrintTimeSeconds() { return printTimeSeconds; }
    public void setPrintTimeSeconds(Integer printTimeSeconds) { this.printTimeSeconds = printTimeSeconds; }

    public BigDecimal getMaterialGrams() { return materialGrams; }
    public void setMaterialGrams(BigDecimal materialGrams) { this.materialGrams = materialGrams; }

    public BigDecimal getUnitPriceChf() { return unitPriceChf; }
    public void setUnitPriceChf(BigDecimal unitPriceChf) { this.unitPriceChf = unitPriceChf; }

    public BigDecimal getLineTotalChf() { return lineTotalChf; }
    public void setLineTotalChf(BigDecimal lineTotalChf) { this.lineTotalChf = lineTotalChf; }
}

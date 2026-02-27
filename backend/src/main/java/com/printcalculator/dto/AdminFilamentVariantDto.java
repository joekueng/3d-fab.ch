package com.printcalculator.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AdminFilamentVariantDto {
    private Long id;
    private Long materialTypeId;
    private String materialCode;
    private Boolean materialIsFlexible;
    private Boolean materialIsTechnical;
    private String materialTechnicalTypeLabel;
    private String variantDisplayName;
    private String colorName;
    private Boolean isMatte;
    private Boolean isSpecial;
    private BigDecimal costChfPerKg;
    private BigDecimal stockSpools;
    private BigDecimal spoolNetKg;
    private BigDecimal stockKg;
    private Boolean isActive;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialTypeId() {
        return materialTypeId;
    }

    public void setMaterialTypeId(Long materialTypeId) {
        this.materialTypeId = materialTypeId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public Boolean getMaterialIsFlexible() {
        return materialIsFlexible;
    }

    public void setMaterialIsFlexible(Boolean materialIsFlexible) {
        this.materialIsFlexible = materialIsFlexible;
    }

    public Boolean getMaterialIsTechnical() {
        return materialIsTechnical;
    }

    public void setMaterialIsTechnical(Boolean materialIsTechnical) {
        this.materialIsTechnical = materialIsTechnical;
    }

    public String getMaterialTechnicalTypeLabel() {
        return materialTechnicalTypeLabel;
    }

    public void setMaterialTechnicalTypeLabel(String materialTechnicalTypeLabel) {
        this.materialTechnicalTypeLabel = materialTechnicalTypeLabel;
    }

    public String getVariantDisplayName() {
        return variantDisplayName;
    }

    public void setVariantDisplayName(String variantDisplayName) {
        this.variantDisplayName = variantDisplayName;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public Boolean getIsMatte() {
        return isMatte;
    }

    public void setIsMatte(Boolean isMatte) {
        this.isMatte = isMatte;
    }

    public Boolean getIsSpecial() {
        return isSpecial;
    }

    public void setIsSpecial(Boolean isSpecial) {
        this.isSpecial = isSpecial;
    }

    public BigDecimal getCostChfPerKg() {
        return costChfPerKg;
    }

    public void setCostChfPerKg(BigDecimal costChfPerKg) {
        this.costChfPerKg = costChfPerKg;
    }

    public BigDecimal getStockSpools() {
        return stockSpools;
    }

    public void setStockSpools(BigDecimal stockSpools) {
        this.stockSpools = stockSpools;
    }

    public BigDecimal getSpoolNetKg() {
        return spoolNetKg;
    }

    public void setSpoolNetKg(BigDecimal spoolNetKg) {
        this.spoolNetKg = spoolNetKg;
    }

    public BigDecimal getStockKg() {
        return stockKg;
    }

    public void setStockKg(BigDecimal stockKg) {
        this.stockKg = stockKg;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.printcalculator.dto;

import java.math.BigDecimal;

public class AdminUpsertFilamentVariantRequest {
    private Long materialTypeId;
    private String variantDisplayName;
    private String colorName;
    private String colorHex;
    private String finishType;
    private String brand;
    private Boolean isMatte;
    private Boolean isSpecial;
    private BigDecimal costChfPerKg;
    private BigDecimal stockSpools;
    private BigDecimal spoolNetKg;
    private Boolean isActive;

    public Long getMaterialTypeId() {
        return materialTypeId;
    }

    public void setMaterialTypeId(Long materialTypeId) {
        this.materialTypeId = materialTypeId;
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

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getFinishType() {
        return finishType;
    }

    public void setFinishType(String finishType) {
        this.finishType = finishType;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

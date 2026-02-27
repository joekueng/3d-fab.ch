package com.printcalculator.dto;

import java.math.BigDecimal;

public class AdminFilamentStockDto {
    private Long filamentVariantId;
    private String materialCode;
    private String variantDisplayName;
    private String colorName;
    private BigDecimal stockSpools;
    private BigDecimal spoolNetKg;
    private BigDecimal stockKg;
    private Boolean active;

    public Long getFilamentVariantId() {
        return filamentVariantId;
    }

    public void setFilamentVariantId(Long filamentVariantId) {
        this.filamentVariantId = filamentVariantId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

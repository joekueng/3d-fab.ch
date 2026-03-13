package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class AdminUpsertShopProductVariantRequest {
    private UUID id;
    private String sku;
    private String variantLabel;
    private String colorName;
    private String colorLabelIt;
    private String colorLabelEn;
    private String colorLabelDe;
    private String colorLabelFr;
    private String colorHex;
    private String internalMaterialCode;
    private BigDecimal priceChf;
    private Boolean isDefault;
    private Boolean isActive;
    private Integer sortOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public void setVariantLabel(String variantLabel) {
        this.variantLabel = variantLabel;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getColorLabelIt() {
        return colorLabelIt;
    }

    public void setColorLabelIt(String colorLabelIt) {
        this.colorLabelIt = colorLabelIt;
    }

    public String getColorLabelEn() {
        return colorLabelEn;
    }

    public void setColorLabelEn(String colorLabelEn) {
        this.colorLabelEn = colorLabelEn;
    }

    public String getColorLabelDe() {
        return colorLabelDe;
    }

    public void setColorLabelDe(String colorLabelDe) {
        this.colorLabelDe = colorLabelDe;
    }

    public String getColorLabelFr() {
        return colorLabelFr;
    }

    public void setColorLabelFr(String colorLabelFr) {
        this.colorLabelFr = colorLabelFr;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getInternalMaterialCode() {
        return internalMaterialCode;
    }

    public void setInternalMaterialCode(String internalMaterialCode) {
        this.internalMaterialCode = internalMaterialCode;
    }

    public BigDecimal getPriceChf() {
        return priceChf;
    }

    public void setPriceChf(BigDecimal priceChf) {
        this.priceChf = priceChf;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}

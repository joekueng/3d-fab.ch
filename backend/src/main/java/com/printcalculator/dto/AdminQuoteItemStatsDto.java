package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class AdminQuoteItemStatsDto {
    private UUID id;
    private String displayName;
    private Integer quantity;
    private Integer printTimeSeconds;
    private BigDecimal materialGrams;
    private BigDecimal unitPriceChf;
    private BigDecimal newUnitPriceChf;
    private String status;
    private String lineItemType;
    private boolean editable;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getPrintTimeSeconds() {
        return printTimeSeconds;
    }

    public void setPrintTimeSeconds(Integer printTimeSeconds) {
        this.printTimeSeconds = printTimeSeconds;
    }

    public BigDecimal getMaterialGrams() {
        return materialGrams;
    }

    public void setMaterialGrams(BigDecimal materialGrams) {
        this.materialGrams = materialGrams;
    }

    public BigDecimal getUnitPriceChf() {
        return unitPriceChf;
    }

    public void setUnitPriceChf(BigDecimal unitPriceChf) {
        this.unitPriceChf = unitPriceChf;
    }

    public BigDecimal getNewUnitPriceChf() {
        return newUnitPriceChf;
    }

    public void setNewUnitPriceChf(BigDecimal newUnitPriceChf) {
        this.newUnitPriceChf = newUnitPriceChf;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLineItemType() {
        return lineItemType;
    }

    public void setLineItemType(String lineItemType) {
        this.lineItemType = lineItemType;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}

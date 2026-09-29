package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminQuoteItemsResponse {
    private UUID sessionId;
    private String sessionStatus;
    private List<AdminQuoteItemStatsDto> items = new ArrayList<>();
    private BigDecimal printItemsTotalChf;
    private BigDecimal globalMachineCostChf;
    private BigDecimal cadTotalChf;
    private BigDecimal itemsTotalChf;
    private BigDecimal setupCostChf;
    private BigDecimal shippingCostChf;
    private BigDecimal grandTotalChf;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public List<AdminQuoteItemStatsDto> getItems() {
        return items;
    }

    public void setItems(List<AdminQuoteItemStatsDto> items) {
        this.items = items;
    }

    public BigDecimal getPrintItemsTotalChf() {
        return printItemsTotalChf;
    }

    public void setPrintItemsTotalChf(BigDecimal printItemsTotalChf) {
        this.printItemsTotalChf = printItemsTotalChf;
    }

    public BigDecimal getGlobalMachineCostChf() {
        return globalMachineCostChf;
    }

    public void setGlobalMachineCostChf(BigDecimal globalMachineCostChf) {
        this.globalMachineCostChf = globalMachineCostChf;
    }

    public BigDecimal getCadTotalChf() {
        return cadTotalChf;
    }

    public void setCadTotalChf(BigDecimal cadTotalChf) {
        this.cadTotalChf = cadTotalChf;
    }

    public BigDecimal getItemsTotalChf() {
        return itemsTotalChf;
    }

    public void setItemsTotalChf(BigDecimal itemsTotalChf) {
        this.itemsTotalChf = itemsTotalChf;
    }

    public BigDecimal getSetupCostChf() {
        return setupCostChf;
    }

    public void setSetupCostChf(BigDecimal setupCostChf) {
        this.setupCostChf = setupCostChf;
    }

    public BigDecimal getShippingCostChf() {
        return shippingCostChf;
    }

    public void setShippingCostChf(BigDecimal shippingCostChf) {
        this.shippingCostChf = shippingCostChf;
    }

    public BigDecimal getGrandTotalChf() {
        return grandTotalChf;
    }

    public void setGrandTotalChf(BigDecimal grandTotalChf) {
        this.grandTotalChf = grandTotalChf;
    }
}

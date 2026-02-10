package com.printcalculator.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public class QuoteResult {
    private double totalPrice;
    private String currency;
    private PrintStats stats;
    
    @JsonIgnore
    private CostBreakdown breakdown;
    
    @JsonIgnore
    private List<String> notes;
    
    private double setupCost;

    public QuoteResult(double totalPrice, String currency, PrintStats stats, CostBreakdown breakdown, List<String> notes, double setupCost) {
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.stats = stats;
        this.breakdown = breakdown;
        this.notes = notes;
        this.setupCost = setupCost;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public PrintStats getStats() {
        return stats;
    }

    public CostBreakdown getBreakdown() {
        return breakdown;
    }

    public List<String> getNotes() {
        return notes;
    }
    
    public double getSetupCost() {
        return setupCost;
    }
}

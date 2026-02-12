package com.printcalculator.model;

public class QuoteResult {
    private double totalPrice;
    private String currency;
    private PrintStats stats;
    private double setupCost;

    public QuoteResult(double totalPrice, String currency, PrintStats stats, double setupCost) {
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.stats = stats;
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
    
    public double getSetupCost() {
        return setupCost;
    }
}

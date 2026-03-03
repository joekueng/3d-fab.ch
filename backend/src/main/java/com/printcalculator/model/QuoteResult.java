package com.printcalculator.model;

public class QuoteResult {
    private double totalPrice;
    private String currency;
    private PrintStats stats;
    public QuoteResult(double totalPrice, String currency, PrintStats stats) {
        this.totalPrice = totalPrice;
        this.currency = currency;
        this.stats = stats;
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
}

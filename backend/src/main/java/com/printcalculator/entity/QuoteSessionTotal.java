package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "quote_session_totals")
public class QuoteSessionTotal {
    @Column(name = "quote_session_id")
    private UUID quoteSessionId;

    @Column(name = "total_chf")
    private BigDecimal totalChf;

    public UUID getQuoteSessionId() {
        return quoteSessionId;
    }

    public BigDecimal getTotalChf() {
        return totalChf;
    }

}
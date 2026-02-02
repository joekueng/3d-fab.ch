package com.printcalculator.model;

import java.math.BigDecimal;
import java.util.List;

public record QuoteResult(
    BigDecimal totalPrice,
    String currency,
    PrintStats stats,
    CostBreakdown breakdown,
    List<String> notes
) {}

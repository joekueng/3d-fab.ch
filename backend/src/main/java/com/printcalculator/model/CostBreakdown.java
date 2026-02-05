package com.printcalculator.model;

import java.math.BigDecimal;

public record CostBreakdown(
    BigDecimal materialCost,
    BigDecimal machineCost,
    BigDecimal energyCost,
    BigDecimal subtotal,
    BigDecimal markupAmount
) {}

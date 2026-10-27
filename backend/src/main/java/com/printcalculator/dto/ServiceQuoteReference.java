package com.printcalculator.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable commercial reference copied from accepted quote into the job and order. */
public record ServiceQuoteReference(UUID quoteId, UUID revisionId, UUID acceptanceId, int revisionNumber,
    String title, BigDecimal estimatedAmountChf, BigDecimal maximumAmountChf,
    String variationType, BigDecimal variationValue) {}

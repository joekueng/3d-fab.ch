package com.printcalculator.model;

public record PrintStats(
    long printTimeSeconds,
    String printTimeFormatted,
    double filamentWeightGrams,
    double filamentLengthMm
) {}

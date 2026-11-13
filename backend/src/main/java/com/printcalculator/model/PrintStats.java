package com.printcalculator.model;

import java.util.List;

public record PrintStats(
    long printTimeSeconds,
    String printTimeFormatted,
    double filamentWeightGrams,
    double filamentLengthMm,
    List<PlateStats> plates
) {
    public PrintStats(long seconds, String formatted, double grams, double lengthMm) {
        this(seconds, formatted, grams, lengthMm, List.of());
    }

    public PrintStats {
        plates = List.copyOf(plates);
    }

    public record PlateStats(int plateNumber, long printTimeSeconds, double filamentWeightGrams,
                             double filamentLengthMm) {
    }
}

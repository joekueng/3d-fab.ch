package com.printcalculator.tools;

public record CalibrationPrintStats(
    long printTimeSeconds,
    String printTimeFormatted,
    double filamentWeightGrams,
    double filamentLengthMm,
    Long modelPrintTimeSeconds,
    Integer layerCount
) {
    public CalibrationPrintStats(long printTimeSeconds, String printTimeFormatted,
                      double filamentWeightGrams, double filamentLengthMm) {
        this(printTimeSeconds, printTimeFormatted, filamentWeightGrams, filamentLengthMm, null, null);
    }
}

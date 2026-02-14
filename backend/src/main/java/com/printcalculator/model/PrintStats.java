package com.printcalculator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PrintStats {
    private long printTimeSeconds;
    private String printTimeFormatted;
    private double filamentWeightGrams;
    private double filamentLengthMm;
    
    // Breakdown if available
    private Double modelWeightGrams;
    private Double supportWeightGrams;

    // Legacy constructor for compatibility
    public PrintStats(long printTimeSeconds, String printTimeFormatted, double filamentWeightGrams, double filamentLengthMm) {
        this.printTimeSeconds = printTimeSeconds;
        this.printTimeFormatted = printTimeFormatted;
        this.filamentWeightGrams = filamentWeightGrams;
        this.filamentLengthMm = filamentLengthMm;
    }
}

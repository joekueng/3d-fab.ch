package com.printcalculator.dto;

public record AdminTranslateFilamentColorsResponse(
        int translatedColors,
        int filamentVariants,
        int shopVariants
) {
}

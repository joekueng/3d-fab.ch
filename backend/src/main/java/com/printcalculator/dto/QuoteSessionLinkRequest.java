package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record QuoteSessionLinkRequest(
        @NotNull @Pattern(regexp = "it|en|de|fr") String language,
        @NotNull @Pattern(regexp = "easy|advanced") String mode,
        @NotNull @Valid InformationDto.DraftLink information) {}

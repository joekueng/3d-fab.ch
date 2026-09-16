package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record QuoteSessionEmailRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Pattern(regexp = "it|en|de|fr") String language,
        @NotNull @Pattern(regexp = "easy|advanced") String mode,
        @NotNull @Valid InformationDto.DraftLink information) {}

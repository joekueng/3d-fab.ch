package com.printcalculator.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCadInvoiceDeleteRequest(
        @NotNull LinkAction linkAction,
        @Size(max = 1000) String targetPath
) {
    public enum LinkAction { NEW_INVOICE, REDIRECT }
}

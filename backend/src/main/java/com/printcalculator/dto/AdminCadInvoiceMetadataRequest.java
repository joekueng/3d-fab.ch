package com.printcalculator.dto;

import jakarta.validation.constraints.Size;

public record AdminCadInvoiceMetadataRequest(
        @Size(max = 160) String invoiceName,
        @Size(max = 160) String clientName,
        @Size(max = 160) String collaborationName
) {}

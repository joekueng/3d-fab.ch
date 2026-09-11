package com.printcalculator.dto;

import com.printcalculator.entity.OrderInformation.Entry;
import jakarta.validation.constraints.*;
import java.util.*;

public record InformationDto(UUID id, List<Entry> entries,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String customerToken) {
    public InformationDto(UUID id, List<Entry> entries) { this(id, entries, null); }

    public record Credential(UUID id, String token) {}
    public record DraftLink(@NotNull UUID id, @NotBlank @Size(max = 100) String token) {}
    public record EntryRequest(@Size(max = 5000) String text, @Size(max = 255) String model, @Size(max = 100) String modelKey,
                               @Size(max = 10) List<UUID> attachments) {}
    public record ReadRequest(@NotNull @Size(max = 100) List<UUID> entries) {}
}

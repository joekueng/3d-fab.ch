package com.printcalculator.event;

import java.util.UUID;

public record CustomQuoteRequestCreatedEvent(UUID requestId, int attachmentsCount, String language) {
}

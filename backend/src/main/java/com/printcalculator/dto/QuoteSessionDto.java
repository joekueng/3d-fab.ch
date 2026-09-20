package com.printcalculator.dto;

import com.printcalculator.entity.QuoteSession;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Public session snapshot, materialized inside the transaction without Hibernate proxies. */
public record QuoteSessionDto(
        UUID id,
        UUID informationDraftId,
        String status,
        String sessionType,
        String pricingVersion,
        String materialCode,
        BigDecimal nozzleDiameterMm,
        BigDecimal layerHeightMm,
        String infillPattern,
        Integer infillPercent,
        Boolean supportsEnabled,
        String notes,
        BigDecimal setupCostChf,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        UUID convertedOrderId,
        UUID sourceRequestId,
        BigDecimal cadHours,
        BigDecimal cadHourlyRateChf) {
    public static QuoteSessionDto from(QuoteSession session) {
        return from(session, true);
    }

    public static QuoteSessionDto forOrderedSessionReuse(QuoteSession session) {
        return from(session, false);
    }

    private static QuoteSessionDto from(QuoteSession session, boolean includeInformationDraft) {
        return new QuoteSessionDto(
                session.getId(),
                includeInformationDraft ? session.getInformationDraftId() : null,
                session.getStatus(),
                session.getSessionType(),
                session.getPricingVersion(),
                session.getMaterialCode(),
                session.getNozzleDiameterMm(),
                session.getLayerHeightMm(),
                session.getInfillPattern(),
                session.getInfillPercent(),
                session.getSupportsEnabled(),
                session.getNotes(),
                session.getSetupCostChf(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getConvertedOrderId(),
                session.getSourceRequestId(),
                session.getCadHours(),
                session.getCadHourlyRateChf());
    }
}

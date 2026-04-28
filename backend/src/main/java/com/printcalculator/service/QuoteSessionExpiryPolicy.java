package com.printcalculator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class QuoteSessionExpiryPolicy {
    private final long ttlMonths;

    public QuoteSessionExpiryPolicy(@Value("${quote.session.ttl-months:6}") long ttlMonths) {
        if (ttlMonths < 1) {
            throw new IllegalStateException("QUOTE_SESSION_TTL_MONTHS must be > 0");
        }
        this.ttlMonths = ttlMonths;
    }

    public OffsetDateTime newExpiry() {
        return OffsetDateTime.now().plusMonths(ttlMonths);
    }

    public Duration cookieMaxAge() {
        OffsetDateTime now = OffsetDateTime.now();
        return Duration.between(now, now.plusMonths(ttlMonths));
    }

    public boolean isExpired(OffsetDateTime expiresAt) {
        return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }
}

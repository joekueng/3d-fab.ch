package com.printcalculator.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-IP sliding budgets and server-issued, session-bound calculation permits. */
@Service
public class QuoteRateLimitService {
    private static final long PERMIT_IDLE_MILLIS = 15 * 60_000L;
    private static final int MAX_ACTIVE_PERMITS = 5_000;

    private final Map<String, ClientBudget> budgets = new HashMap<>();
    private final Map<UUID, CalculationPermit> permits = new HashMap<>();
    private final int maxRequests;
    private final int maxCalculations;
    private final int maxFiles;
    private final long windowMillis;
    private final boolean trustProxyHeaders;
    private final Clock clock;
    private int operations;

    @Autowired
    public QuoteRateLimitService(
            @Value("${quote.rate-limit.max-requests:15}") int maxRequests,
            @Value("${quote.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${quote.rate-limit.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${quote.rate-limit.max-calculations:5}") int maxCalculations,
            @Value("${quote.rate-limit.max-files:75}") int maxFiles) {
        this(maxRequests, windowSeconds, trustProxyHeaders, maxCalculations, maxFiles, Clock.systemUTC());
    }

    QuoteRateLimitService(int maxRequests, long windowSeconds, boolean trustProxyHeaders,
                          int maxCalculations, int maxFiles, Clock clock) {
        this.maxRequests = maxRequests > 0 ? maxRequests : 15;
        this.maxCalculations = maxCalculations > 0 ? maxCalculations : 5;
        this.maxFiles = maxFiles > 0 ? maxFiles : 75;
        this.windowMillis = windowSeconds > 0 ? windowSeconds * 1000L : 60_000L;
        this.trustProxyHeaders = trustProxyHeaders;
        this.clock = clock;
    }

    /** Reserve the whole batch before clearing an existing quote or accepting uploads. */
    public synchronized UUID reserveCalculation(HttpServletRequest request, int fileCount) {
        if (fileCount < 1 || fileCount > maxFiles) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A calculation must contain between 1 and " + maxFiles + " files");
        }
        long now = clock.millis();
        cleanup(now);
        if (permits.size() >= MAX_ACTIVE_PERMITS) {
            permits.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
            if (permits.size() >= MAX_ACTIVE_PERMITS) {
                throw new SlicingRateLimitException(60);
            }
        }
        String clientKey = resolveClientKey(request);
        record(clientKey, new Admission(now, fileCount, true), now);
        UUID id = UUID.randomUUID();
        permits.put(id, new CalculationPermit(clientKey, fileCount, now + PERMIT_IDLE_MILLIS));
        return id;
    }

    public synchronized void bindCalculation(UUID calculationId, UUID sessionId) {
        CalculationPermit permit = permits.get(calculationId);
        if (permit == null || permit.sessionId != null || permit.expiresAt <= clock.millis()) {
            throw invalidPermit();
        }
        permit.sessionId = sessionId;
    }

    /** Legacy requests retain their 15-request budget and share the batch file budget. */
    public synchronized void checkSlicingAllowed(HttpServletRequest request) {
        long now = clock.millis();
        cleanup(now);
        record(resolveClientKey(request), new Admission(now, 1, false), now);
    }

    public synchronized void checkSlicingAllowed(HttpServletRequest request, UUID sessionId, UUID calculationId) {
        if (calculationId == null) {
            checkSlicingAllowed(request);
            return;
        }
        long now = clock.millis();
        cleanup(now);
        CalculationPermit permit = permits.get(calculationId);
        if (permit == null || permit.expiresAt <= now
                || !permit.clientKey.equals(resolveClientKey(request))
                || !sessionId.equals(permit.sessionId)) {
            throw invalidPermit();
        }
        // Atomic consumption prevents concurrent uploads from exceeding the reservation.
        permit.remainingFiles--;
        permit.expiresAt = now + PERMIT_IDLE_MILLIS;
        if (permit.remainingFiles == 0) {
            permits.remove(calculationId);
        }
    }

    private void record(String clientKey, Admission admission, long now) {
        ClientBudget budget = budgets.computeIfAbsent(clientKey, key -> new ClientBudget());
        budget.admissions.removeIf(event -> event.at <= now - windowMillis);
        int files = budget.admissions.stream().mapToInt(event -> event.files).sum();
        long calculations = budget.admissions.stream().filter(event -> event.calculation).count();
        long legacyRequests = budget.admissions.size() - calculations;
        boolean blocked = files + admission.files > maxFiles
                || (admission.calculation ? calculations >= maxCalculations : legacyRequests >= maxRequests);
        if (blocked) {
            // Find when enough accepted work expires. Rejections never extend the window.
            for (Admission event : budget.admissions) {
                files -= event.files;
                if (event.calculation) calculations--; else legacyRequests--;
                if (files + admission.files <= maxFiles
                        && (admission.calculation ? calculations < maxCalculations : legacyRequests < maxRequests)) {
                    throw new SlicingRateLimitException(Math.max(1, (event.at + windowMillis - now + 999) / 1000));
                }
            }
        }
        budget.admissions.addLast(admission);
    }

    private void cleanup(long now) {
        if ((++operations & 0xFF) == 0) {
            budgets.entrySet().removeIf(entry -> entry.getValue().admissions.isEmpty()
                    || entry.getValue().admissions.getLast().at <= now - windowMillis);
            permits.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        }
    }

    private ResponseStatusException invalidPermit() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired calculation permit");
    }

    public String resolveClientKey(HttpServletRequest request) {
        if (trustProxyHeaders && request != null) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",", -1)[0].trim();
                if (!first.isEmpty()) return first;
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) return realIp.trim();
        }
        if (request == null) return "unknown";
        String address = request.getRemoteAddr();
        return address != null && !address.isBlank() ? address.trim() : "unknown";
    }

    private record Admission(long at, int files, boolean calculation) {}

    private static final class ClientBudget {
        private final ArrayDeque<Admission> admissions = new ArrayDeque<>();
    }

    private static final class CalculationPermit {
        private final String clientKey;
        private int remainingFiles;
        private long expiresAt;
        private UUID sessionId;

        private CalculationPermit(String clientKey, int remainingFiles, long expiresAt) {
            this.clientKey = clientKey;
            this.remainingFiles = remainingFiles;
            this.expiresAt = expiresAt;
        }
    }

    private static final class SlicingRateLimitException extends ResponseStatusException {
        private final HttpHeaders headers = new HttpHeaders();

        private SlicingRateLimitException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a moment and try again.");
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }

        @Override
        public HttpHeaders getHeaders() { return headers; }
    }
}

package com.printcalculator.service.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class QrTrackingRequestService {
    private final String visitorHashSecret;
    private final boolean trustProxyHeaders;
    private final boolean geoEnabled;

    public QrTrackingRequestService(
            @Value("${app.qr.visitor-hash-secret:${ADMIN_SESSION_SECRET:change-me-change-me-change-me-change-me}}")
            String visitorHashSecret,
            @Value("${app.qr.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.qr.geo.enabled:false}") boolean geoEnabled
    ) {
        this.visitorHashSecret = visitorHashSecret;
        this.trustProxyHeaders = trustProxyHeaders;
        this.geoEnabled = geoEnabled;
    }

    public ResolvedTrackingContext resolve(HttpServletRequest request, UUID qrLinkId) {
        String clientIp = resolveClientIp(request);
        String userAgent = normalizeHeader(request.getHeader("User-Agent"));
        String visitorKeyHash = hashVisitorKey(qrLinkId, clientIp, userAgent);
        boolean suspectedBot = isSuspectedBot(userAgent);

        String countryCode = null;
        String countryName = null;
        String cityName = null;
        if (geoEnabled && trustProxyHeaders) {
            countryCode = firstPresentHeader(request, "X-Geo-Country-Code", "CF-IPCountry");
            countryName = firstPresentHeader(request, "X-Geo-Country-Name");
            cityName = firstPresentHeader(request, "X-Geo-City");
        }

        return new ResolvedTrackingContext(
                clientIp,
                visitorKeyHash,
                suspectedBot,
                emptyToNull(countryCode),
                emptyToNull(countryName),
                emptyToNull(cityName),
                OffsetDateTime.now()
        );
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] parts = forwardedFor.split(",");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    return parts[0].trim();
                }
            }

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }

        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            return remoteAddress.trim();
        }

        return "unknown";
    }

    boolean isSuspectedBot(String userAgent) {
        String normalized = String.valueOf(userAgent == null ? "" : userAgent).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.matches(".*(bot|crawler|spider|slurp|bingpreview|google-read-aloud|headless|preview).*");
    }

    private String hashVisitorKey(UUID qrLinkId, String clientIp, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = visitorHashSecret + "|" + qrLinkId + "|" + clientIp + "|" + userAgent;
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create QR visitor hash", ex);
        }
    }

    private String normalizeHeader(String value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private String firstPresentHeader(HttpServletRequest request, String... headerNames) {
        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank() || "XX".equalsIgnoreCase(value) || "T1".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    public record ResolvedTrackingContext(
            String clientIp,
            String visitorKeyHash,
            boolean suspectedBot,
            String countryCode,
            String countryName,
            String cityName,
            OffsetDateTime scannedAt
    ) {
    }
}

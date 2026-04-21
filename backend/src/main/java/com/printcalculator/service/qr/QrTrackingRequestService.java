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

    public QrTrackingRequestService(
            @Value("${app.qr.visitor-hash-secret:${ADMIN_SESSION_SECRET:change-me-change-me-change-me-change-me}}")
            String visitorHashSecret,
            @Value("${app.qr.trust-proxy-headers:false}") boolean trustProxyHeaders
    ) {
        this.visitorHashSecret = visitorHashSecret;
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public ResolvedTrackingContext resolve(HttpServletRequest request, UUID qrLinkId) {
        String clientIp = resolveClientIp(request);
        String userAgent = normalizeHeader(request.getHeader("User-Agent"));
        String visitorKeyHash = hashVisitorKey(qrLinkId, clientIp, userAgent);
        boolean suspectedBot = isSuspectedBot(userAgent);

        return new ResolvedTrackingContext(
                clientIp,
                visitorKeyHash,
                suspectedBot,
                OffsetDateTime.now()
        );
    }

    public String resolveClientIp(HttpServletRequest request) {
        return IpAddressUtils.resolveClientIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr(),
                trustProxyHeaders
        );
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

    public record ResolvedTrackingContext(
            String clientIp,
            String visitorKeyHash,
            boolean suspectedBot,
            OffsetDateTime scannedAt
    ) {
    }
}

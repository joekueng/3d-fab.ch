package com.printcalculator.service.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class QrTrackingRequestService {
    private static final Logger logger = LoggerFactory.getLogger(QrTrackingRequestService.class);

    private final String visitorHashSecret;
    private final boolean trustProxyHeaders;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public QrTrackingRequestService(
            @Value("${app.qr.visitor-hash-secret:${ADMIN_SESSION_SECRET:change-me-change-me-change-me-change-me}}")
            String visitorHashSecret,
            @Value("${app.qr.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.qr.trusted-proxy-networks:}") String trustedProxyNetworks
    ) {
        this.visitorHashSecret = visitorHashSecret;
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxyMatchers = IpAddressUtils.parseTrustedProxyMatchers(trustedProxyNetworks);

        if (trustProxyHeaders && this.trustedProxyMatchers.isEmpty()) {
            logger.warn("QR proxy header trust is enabled, but app.qr.trusted-proxy-networks is empty. Forwarded headers will be ignored.");
        }
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
                trustProxyHeaders,
                trustedProxyMatchers
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

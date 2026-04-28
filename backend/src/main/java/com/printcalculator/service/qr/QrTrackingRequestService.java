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
    private static final String BOT_USER_AGENT_PATTERN =
            ".*(bot|crawler|spider|slurp|bingpreview|google-read-aloud|headless|skypeuripreview|facebookexternalhit|meta-externalagent|slackbot|discordbot).*";

    private final String visitorHashSecret;
    private final boolean trustProxyHeaders;
    private final List<IpAddressMatcher> trustedProxyMatchers;
    private final String trustedProxyNetworks;
    private final boolean debugLogging;

    public QrTrackingRequestService(
            @Value("${app.qr.visitor-hash-secret:${ADMIN_SESSION_SECRET:change-me-change-me-change-me-change-me}}")
            String visitorHashSecret,
            @Value("${app.qr.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.qr.trusted-proxy-networks:}") String trustedProxyNetworks,
            @Value("${app.qr.debug-logging:false}") boolean debugLogging
    ) {
        this.visitorHashSecret = visitorHashSecret;
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxyNetworks = String.valueOf(trustedProxyNetworks == null ? "" : trustedProxyNetworks).trim();
        this.trustedProxyMatchers = IpAddressUtils.parseTrustedProxyMatchers(trustedProxyNetworks);
        this.debugLogging = debugLogging;

        if (trustProxyHeaders && this.trustedProxyMatchers.isEmpty()) {
            logger.warn("QR proxy header trust is enabled, but app.qr.trusted-proxy-networks is empty. Forwarded headers will be ignored.");
        }
    }

    public ResolvedTrackingContext resolve(HttpServletRequest request, UUID qrLinkId) {
        String clientIp = resolveClientIp(request);
        String userAgent = normalizeHeader(request.getHeader("User-Agent"));
        String visitorKeyHash = hashVisitorKey(qrLinkId, clientIp, userAgent);
        boolean suspectedBot = isSuspectedBot(userAgent);

        if (debugLogging) {
            logRequestDebug(qrLinkId, request, clientIp, suspectedBot);
        }

        return new ResolvedTrackingContext(
                clientIp,
                visitorKeyHash,
                suspectedBot,
                OffsetDateTime.now()
        );
    }

    public String resolveClientIp(HttpServletRequest request) {
        return IpAddressUtils.resolveClientIp(
                request.getHeader("Forwarded"),
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
        return normalized.matches(BOT_USER_AGENT_PATTERN);
    }

    private void logRequestDebug(UUID qrLinkId,
                                 HttpServletRequest request,
                                 String clientIp,
                                 boolean suspectedBot) {
        String remoteAddress = request.getRemoteAddr();
        String normalizedRemoteAddress = IpAddressUtils.normalizeIp(remoteAddress);
        boolean trustedProxy = trustProxyHeaders && IpAddressUtils.isTrustedProxy(normalizedRemoteAddress, trustedProxyMatchers);

        logger.info(
                "QR debug: qrLinkId={}, remoteAddrRaw={}, remoteAddrNormalized={}, forwarded={}, xForwardedFor={}, xRealIp={}, trustProxyHeaders={}, trustedProxy={}, trustedProxyNetworks={}, resolvedClientIp={}, resolvedClientIpPublic={}, suspectedBot={}",
                qrLinkId,
                normalizeHeader(remoteAddress),
                normalizedRemoteAddress,
                normalizeHeader(request.getHeader("Forwarded")),
                normalizeHeader(request.getHeader("X-Forwarded-For")),
                normalizeHeader(request.getHeader("X-Real-IP")),
                trustProxyHeaders,
                trustedProxy,
                trustedProxyNetworks,
                clientIp,
                IpAddressUtils.isPublicIp(clientIp),
                suspectedBot
        );
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

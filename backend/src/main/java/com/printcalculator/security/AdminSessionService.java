package com.printcalculator.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminSessionService {

    public static final String COOKIE_NAME = "admin_session";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String adminPassword;
    private final byte[] sessionSecret;
    private final long sessionTtlMinutes;

    public AdminSessionService(
            ObjectMapper objectMapper,
            @Value("${admin.password}") String adminPassword,
            @Value("${admin.session.secret}") String sessionSecret,
            @Value("${admin.session.ttl-minutes}") long sessionTtlMinutes
    ) {
        this.objectMapper = objectMapper;
        this.adminPassword = adminPassword;
        this.sessionSecret = sessionSecret.getBytes(StandardCharsets.UTF_8);
        this.sessionTtlMinutes = sessionTtlMinutes;

        validateConfiguration(adminPassword, sessionSecret, sessionTtlMinutes);
    }

    public boolean isPasswordValid(String candidatePassword) {
        if (candidatePassword == null) {
            return false;
        }

        return MessageDigest.isEqual(
                adminPassword.getBytes(StandardCharsets.UTF_8),
                candidatePassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String createSessionToken() {
        Instant now = Instant.now();
        AdminSessionPayload payload = new AdminSessionPayload(
                now.getEpochSecond(),
                now.plus(Duration.ofMinutes(sessionTtlMinutes)).getEpochSecond(),
                UUID.randomUUID().toString()
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = base64UrlEncode(sign(encodedPayload));
            return encodedPayload + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot create admin session token", e);
        }
    }

    public Optional<AdminSessionPayload> validateSessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }

        String encodedPayload = parts[0];
        String encodedSignature = parts[1];
        byte[] providedSignature;
        try {
            providedSignature = base64UrlDecode(encodedSignature);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        byte[] expectedSignature = sign(encodedPayload);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            return Optional.empty();
        }

        try {
            byte[] decodedPayload = base64UrlDecode(encodedPayload);
            AdminSessionPayload payload = objectMapper.readValue(decodedPayload, AdminSessionPayload.class);
            if (payload.exp <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (IllegalArgumentException | IOException e) {
            return Optional.empty();
        }
    }

    public Optional<String> extractTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }

        return Optional.empty();
    }

    public ResponseCookie buildLoginCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(sessionTtlMinutes))
                .build();
    }

    public ResponseCookie buildLogoutCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }

    public long getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sessionSecret, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign admin session token", e);
        }
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private void validateConfiguration(String password, String secret, long ttlMinutes) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD must be configured and non-empty");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("ADMIN_SESSION_SECRET must be configured and non-empty");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("ADMIN_SESSION_SECRET must be at least 32 characters long");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalStateException("ADMIN_SESSION_TTL_MINUTES must be > 0");
        }
    }

    public static class AdminSessionPayload {
        @JsonProperty("iat")
        public long iat;
        @JsonProperty("exp")
        public long exp;
        @JsonProperty("nonce")
        public String nonce;

        public AdminSessionPayload() {
        }

        public AdminSessionPayload(long iat, long exp, String nonce) {
            this.iat = iat;
            this.exp = exp;
            this.nonce = nonce;
        }
    }
}

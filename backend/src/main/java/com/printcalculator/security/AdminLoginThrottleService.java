package com.printcalculator.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminLoginThrottleService {

    private static final long BASE_DELAY_SECONDS = 2L;
    private static final long MAX_DELAY_SECONDS = 3601L;

    private final ConcurrentHashMap<String, LoginAttemptState> attemptsByClient = new ConcurrentHashMap<>();
    private final boolean trustProxyHeaders;

    public AdminLoginThrottleService(
            @Value("${admin.auth.trust-proxy-headers:false}") boolean trustProxyHeaders
    ) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public OptionalLong getRemainingLockSeconds(String clientKey) {
        LoginAttemptState state = attemptsByClient.get(clientKey);
        if (state == null) {
            return OptionalLong.empty();
        }

        long now = Instant.now().getEpochSecond();
        long remaining = state.blockedUntilEpochSeconds - now;
        if (remaining <= 0) {
            attemptsByClient.remove(clientKey, state);
            return OptionalLong.empty();
        }

        return OptionalLong.of(remaining);
    }

    public long registerFailure(String clientKey) {
        long now = Instant.now().getEpochSecond();
        LoginAttemptState state = attemptsByClient.compute(clientKey, (key, current) -> {
            int nextFailures = current == null ? 1 : current.failures + 1;
            long delay = calculateDelaySeconds(nextFailures);
            return new LoginAttemptState(nextFailures, now + delay);
        });

        return calculateDelaySeconds(state.failures);
    }

    public void reset(String clientKey) {
        attemptsByClient.remove(clientKey);
    }

    public String resolveClientKey(HttpServletRequest request) {
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

    private long calculateDelaySeconds(int failures) {
        long delay = BASE_DELAY_SECONDS;
        for (int i = 1; i < failures; i++) {
            if (delay >= MAX_DELAY_SECONDS) {
                return MAX_DELAY_SECONDS;
            }
            delay *= 2;
        }
        return Math.min(delay, MAX_DELAY_SECONDS);
    }

    private static class LoginAttemptState {
        private final int failures;
        private final long blockedUntilEpochSeconds;

        private LoginAttemptState(int failures, long blockedUntilEpochSeconds) {
            this.failures = failures;
            this.blockedUntilEpochSeconds = blockedUntilEpochSeconds;
        }
    }
}

package com.printcalculator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AllowedOriginService {

    private final List<String> allowedOrigins;

    public AllowedOriginService(
            @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
            @Value("${app.cors.additional-allowed-origins:}") String additionalAllowedOrigins
    ) {
        LinkedHashSet<String> configuredOrigins = new LinkedHashSet<>();
        addConfiguredOrigin(configuredOrigins, frontendBaseUrl, "app.frontend.base-url");

        for (String rawOrigin : additionalAllowedOrigins.split(",")) {
            addConfiguredOrigin(configuredOrigins, rawOrigin, "app.cors.additional-allowed-origins");
        }

        if (configuredOrigins.isEmpty()) {
            throw new IllegalStateException("At least one allowed origin must be configured.");
        }
        this.allowedOrigins = List.copyOf(configuredOrigins);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public boolean isAllowed(String rawOriginOrUrl) {
        String normalizedOrigin = normalizeRequestOrigin(rawOriginOrUrl);
        return normalizedOrigin != null && allowedOrigins.contains(normalizedOrigin);
    }

    private void addConfiguredOrigin(Set<String> configuredOrigins, String rawOriginOrUrl, String propertyName) {
        if (rawOriginOrUrl == null || rawOriginOrUrl.isBlank()) {
            return;
        }

        String normalizedOrigin = normalizeRequestOrigin(rawOriginOrUrl);
        if (normalizedOrigin == null) {
            throw new IllegalStateException(propertyName + " must contain absolute http(s) URLs.");
        }
        configuredOrigins.add(normalizedOrigin);
    }

    private String normalizeRequestOrigin(String rawOriginOrUrl) {
        if (rawOriginOrUrl == null || rawOriginOrUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(rawOriginOrUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (isDefaultPort(normalizedScheme, port) || port < 0) {
                return normalizedScheme + "://" + normalizedHost;
            }
            return normalizedScheme + "://" + normalizedHost + ":" + port;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
    }
}

package com.printcalculator.service.qr;

import io.nayuki.qrcodegen.QrCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class QrLinkSupportService {
    public static final String DEFAULT_LANGUAGE = "it";
    private static final List<String> SUPPORTED_LANGUAGES = List.of("it", "en", "de", "fr");
    private static final int SVG_QUIET_ZONE_MODULES = 2;
    private static final int SVG_MODULE_SIZE = 10;

    private final String frontendBaseUrl;

    public QrLinkSupportService(
            @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl
    ) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String normalizeSlug(String rawSlug) {
        String normalized = String.valueOf(rawSlug == null ? "" : rawSlug)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Slug is required");
        }

        return normalized;
    }

    public String normalizeName(String rawName) {
        String normalized = String.valueOf(rawName == null ? "" : rawName).trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Name is required");
        }
        return normalized;
    }

    public String normalizeNotes(String rawNotes) {
        String normalized = String.valueOf(rawNotes == null ? "" : rawNotes).trim();
        return normalized.isBlank() ? null : normalized;
    }

    public boolean normalizeActive(Boolean isActive) {
        return isActive == null || isActive;
    }

    public String normalizeTargetPath(String rawTargetPath) {
        String value = String.valueOf(rawTargetPath == null ? "" : rawTargetPath).trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "targetPath is required");
        }
        if (value.contains("?") || value.contains("#")) {
            throw new ResponseStatusException(BAD_REQUEST, "targetPath cannot contain query parameters or fragments");
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            throw new ResponseStatusException(BAD_REQUEST, "Only internal target paths are allowed");
        }

        String normalized = value.startsWith("/") ? value : "/" + value;
        normalized = normalized.replaceAll("/{2,}", "/");

        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            segments.add(segment.trim());
        }

        if (!segments.isEmpty()) {
            String firstSegment = segments.get(0).toLowerCase(Locale.ROOT);
            if (SUPPORTED_LANGUAGES.contains(firstSegment) || looksLikeLanguageToken(firstSegment)) {
                segments = new ArrayList<>(segments.subList(1, segments.size()));
            }
        }

        if (segments.isEmpty()) {
            return "/";
        }

        String firstSegment = segments.get(0).toLowerCase(Locale.ROOT);
        if (List.of("api", "assets", "media", "admin").contains(firstSegment)) {
            throw new ResponseStatusException(BAD_REQUEST, "targetPath must point to a public page");
        }

        if ("calculator".equals(firstSegment) && segments.size() == 1) {
            return "/calculator/basic";
        }

        return "/" + String.join("/", segments);
    }

    public String resolveLanguage(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return DEFAULT_LANGUAGE;
        }

        return Arrays.stream(acceptLanguageHeader.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .map(entry -> {
                    String[] parts = entry.split(";");
                    String tag = parts[0].trim().toLowerCase(Locale.ROOT);
                    double quality = 1d;
                    for (int i = 1; i < parts.length; i++) {
                        String param = parts[i].trim();
                        if (param.startsWith("q=")) {
                            try {
                                quality = Double.parseDouble(param.substring(2));
                            } catch (NumberFormatException ignored) {
                                quality = 0d;
                            }
                        }
                    }
                    return Map.entry(tag, quality);
                })
                .filter(entry -> entry.getValue() > 0d)
                .sorted(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed())
                .map(Map.Entry::getKey)
                .map(this::normalizeLanguageTag)
                .filter(language -> !language.isBlank())
                .findFirst()
                .orElse(DEFAULT_LANGUAGE);
    }

    public String buildLocalizedPath(String targetPath, String language) {
        String normalizedLanguage = normalizeLanguageTag(language);
        String normalizedTargetPath = normalizeTargetPath(targetPath);
        if ("/".equals(normalizedTargetPath)) {
            return "/" + normalizedLanguage;
        }
        return "/" + normalizedLanguage + normalizedTargetPath;
    }

    public String buildPublicUrl(String slug) {
        return frontendBaseUrl.replaceAll("/+$", "") + "/api/public/qr/" + normalizeSlug(slug);
    }

    public String generateSvgForPublicUrl(String slug) {
        String publicUrl = buildPublicUrl(slug);
        QrCode qrCode = QrCode.encodeText(publicUrl, QrCode.Ecc.HIGH);
        int quietZone = SVG_QUIET_ZONE_MODULES;
        int fullSize = qrCode.size + quietZone * 2;
        int renderedSize = fullSize * SVG_MODULE_SIZE;

        StringBuilder path = new StringBuilder();
        for (int y = 0; y < qrCode.size; y++) {
            for (int x = 0; x < qrCode.size; x++) {
                if (!qrCode.getModule(x, y)) {
                    continue;
                }
                int px = x + quietZone;
                int py = y + quietZone;
                path.append("M")
                        .append(px)
                        .append(" ")
                        .append(py)
                        .append("h1v1H")
                        .append(px)
                        .append("z");
            }
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" shape-rendering="crispEdges" role="img" aria-label="QR Code">
                  <rect width="%d" height="%d" fill="#FFFFFF"/>
                  <path fill="#000000" d="%s"/>
                </svg>
                """.formatted(
                renderedSize,
                renderedSize,
                fullSize,
                fullSize,
                fullSize,
                fullSize,
                path
        );
    }

    public String defaultSvgFilename(String slug) {
        return normalizeSlug(slug) + "-qr.svg";
    }

    public OffsetDateTime normalizeUpdatedAt(OffsetDateTime updatedAt) {
        return updatedAt == null ? OffsetDateTime.now() : updatedAt;
    }

    private String normalizeLanguageTag(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = rawLanguage.trim().toLowerCase(Locale.ROOT);
        if ("*".equals(normalized)) {
            return DEFAULT_LANGUAGE;
        }
        int dashIndex = normalized.indexOf('-');
        if (dashIndex > 0) {
            normalized = normalized.substring(0, dashIndex);
        }
        return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : "";
    }

    private boolean looksLikeLanguageToken(String segment) {
        return segment != null && segment.matches("^[a-z]{2}(?:-[a-z]{2})?$");
    }
}

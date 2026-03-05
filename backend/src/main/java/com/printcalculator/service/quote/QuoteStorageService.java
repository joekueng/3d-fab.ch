package com.printcalculator.service.quote;

import com.printcalculator.entity.QuoteLineItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class QuoteStorageService {
    private static final Path QUOTE_STORAGE_ROOT = Paths.get("storage_quotes").toAbsolutePath().normalize();

    public Path sessionStorageDir(UUID sessionId) throws IOException {
        Path sessionStorageDir = QUOTE_STORAGE_ROOT.resolve(sessionId.toString()).normalize();
        if (!sessionStorageDir.startsWith(QUOTE_STORAGE_ROOT)) {
            throw new IOException("Invalid quote session storage path");
        }
        Files.createDirectories(sessionStorageDir);
        return sessionStorageDir;
    }

    public Path resolveSessionPath(Path sessionStorageDir, String filename) throws IOException {
        Path resolved = sessionStorageDir.resolve(filename).normalize();
        if (!resolved.startsWith(sessionStorageDir)) {
            throw new IOException("Invalid quote line-item storage path");
        }
        return resolved;
    }

    public String toStoredPath(Path absolutePath) {
        return QUOTE_STORAGE_ROOT.relativize(absolutePath).toString();
    }

    public String getSafeExtension(String filename, String fallback) {
        if (filename == null) {
            return fallback;
        }
        String cleaned = StringUtils.cleanPath(filename);
        if (cleaned.contains("..")) {
            return fallback;
        }
        int index = cleaned.lastIndexOf('.');
        if (index <= 0 || index >= cleaned.length() - 1) {
            return fallback;
        }
        String ext = cleaned.substring(index + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "stl" -> "stl";
            case "3mf" -> "3mf";
            case "step", "stp" -> "step";
            default -> fallback;
        };
    }

    public Path resolveStoredQuotePath(String storedPath, UUID expectedSessionId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedPath).normalize();
            Path resolved = raw.isAbsolute() ? raw : QUOTE_STORAGE_ROOT.resolve(raw).normalize();
            Path expectedSessionRoot = QUOTE_STORAGE_ROOT.resolve(expectedSessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public String extractConvertedStoredPath(QuoteLineItem item) {
        Map<String, Object> breakdown = item.getPricingBreakdown();
        if (breakdown == null) {
            return null;
        }
        Object converted = breakdown.get("convertedStoredPath");
        if (converted == null) {
            return null;
        }
        String path = String.valueOf(converted).trim();
        return path.isEmpty() ? null : path;
    }
}

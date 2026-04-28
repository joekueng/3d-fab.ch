package com.printcalculator.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;

final class SlicerFileSupport {
    private SlicerFileSupport() {
    }

    static String normalizeExecutablePath(String configuredPath, String propertyName) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException(propertyName + " is required");
        }
        if (containsControlChars(configuredPath)) {
            throw new IllegalArgumentException(propertyName + " contains invalid control characters");
        }
        try {
            return Path.of(configuredPath.trim()).normalize().toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid " + propertyName + ": " + configuredPath, e);
        }
    }

    static String requireSafeArgument(String value, String argName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required argument: " + argName);
        }
        if (containsControlChars(value)) {
            throw new IOException("Invalid control characters in " + argName);
        }
        return value;
    }

    static void deleteRecursively(Path path, Logger logger) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warning("Failed to delete temp path " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("Failed to walk temp directory " + path + ": " + e.getMessage());
        }
    }

    private static boolean containsControlChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\0' || ch == '\n' || ch == '\r') {
                return true;
            }
        }
        return false;
    }
}

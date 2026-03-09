package com.printcalculator.service.media;

import com.printcalculator.exception.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
public class MediaStorageService {

    private final Path normalizedRootLocation;
    private final Path originalRootLocation;
    private final Path publicRootLocation;
    private final Path privateRootLocation;
    private final String publicBaseUrl;

    public MediaStorageService(@Value("${media.storage.root:storage_media}") String storageRoot,
                               @Value("${media.public.base-url:http://localhost:8080/media}") String publicBaseUrl) {
        this.normalizedRootLocation = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.originalRootLocation = normalizedRootLocation.resolve("original").normalize();
        this.publicRootLocation = normalizedRootLocation.resolve("public").normalize();
        this.privateRootLocation = normalizedRootLocation.resolve("private").normalize();
        this.publicBaseUrl = publicBaseUrl;
        init();
    }

    public void init() {
        try {
            Files.createDirectories(originalRootLocation);
            Files.createDirectories(publicRootLocation);
            Files.createDirectories(privateRootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize media storage.", e);
        }
    }

    public void storeOriginal(Path source, String storageKey) throws IOException {
        copy(source, resolveOriginal(storageKey));
    }

    public void storePublic(Path source, String storageKey) throws IOException {
        copy(source, resolvePublic(storageKey));
    }

    public void storePrivate(Path source, String storageKey) throws IOException {
        copy(source, resolvePrivate(storageKey));
    }

    public void deleteGenerated(String visibility, String storageKey) throws IOException {
        Files.deleteIfExists(resolve(resolveVariantRoot(normalizeVisibility(visibility)), storageKey));
    }

    public void moveGenerated(String storageKey, String fromVisibility, String toVisibility) throws IOException {
        String normalizedFrom = normalizeVisibility(fromVisibility);
        String normalizedTo = normalizeVisibility(toVisibility);
        if (normalizedFrom.equals(normalizedTo)) {
            return;
        }

        Path source = resolve(resolveVariantRoot(normalizedFrom), storageKey);
        Path target = resolve(resolveVariantRoot(normalizedTo), storageKey);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public String buildPublicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        String normalizedKey = storageKey.startsWith("/") ? storageKey.substring(1) : storageKey;
        if (publicBaseUrl.endsWith("/")) {
            return publicBaseUrl + normalizedKey;
        }
        return publicBaseUrl + "/" + normalizedKey;
    }

    private void copy(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveOriginal(String storageKey) {
        return resolve(originalRootLocation, storageKey);
    }

    private Path resolvePublic(String storageKey) {
        return resolve(publicRootLocation, storageKey);
    }

    private Path resolvePrivate(String storageKey) {
        return resolve(privateRootLocation, storageKey);
    }

    private Path resolveVariantRoot(String visibility) {
        return switch (visibility) {
            case "PUBLIC" -> publicRootLocation;
            case "PRIVATE" -> privateRootLocation;
            default -> throw new StorageException("Unsupported media visibility: " + visibility);
        };
    }

    private Path resolve(Path baseRoot, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new StorageException("Storage key is required.");
        }
        Path relativePath = Paths.get(storageKey).normalize();
        if (relativePath.isAbsolute()) {
            throw new StorageException("Absolute paths are not allowed.");
        }

        Path resolved = baseRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseRoot)) {
            throw new StorageException("Cannot access files outside media storage root.");
        }
        return resolved;
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            throw new StorageException("Visibility is required.");
        }
        return visibility.trim().toUpperCase(Locale.ROOT);
    }
}

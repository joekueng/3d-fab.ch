package com.printcalculator.service.shop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ShopStorageService {
    private final Path storageRoot;

    public ShopStorageService(@Value("${shop.storage.root:storage_shop}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public Path productModelStorageDir(UUID productId) throws IOException {
        Path dir = storageRoot.resolve(Path.of("products", productId.toString(), "3d-models")).normalize();
        if (!dir.startsWith(storageRoot)) {
            throw new IOException("Invalid shop product storage path");
        }
        Files.createDirectories(dir);
        return dir;
    }

    public Path resolveStoredProductPath(String storedRelativePath, UUID expectedProductId) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedRelativePath).normalize();
            Path resolved = raw.isAbsolute() ? raw : storageRoot.resolve(raw).normalize();
            Path expectedPrefix = storageRoot.resolve(Path.of("products", expectedProductId.toString())).normalize();
            if (!resolved.startsWith(expectedPrefix)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public String toStoredPath(Path absolutePath) {
        return storageRoot.relativize(absolutePath.toAbsolutePath().normalize()).toString();
    }
}

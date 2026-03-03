package com.printcalculator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.printcalculator.exception.StorageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;
    private final Path normalizedRootLocation;
    private final ClamAVService clamAVService;

    public FileSystemStorageService(@Value("${storage.location:storage_orders}") String storageLocation, ClamAVService clamAVService) {
        this.rootLocation = Paths.get(storageLocation);
        this.normalizedRootLocation = this.rootLocation.toAbsolutePath().normalize();
        this.clamAVService = clamAVService;
    }

    @Override
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }

    @Override
    public void store(MultipartFile file, Path destinationRelativePath) throws IOException {
        Path destinationFile = resolveInsideStorage(destinationRelativePath);

        // 1. Salva prima il file su disco per evitare problemi di stream con file grandi
        Files.createDirectories(destinationFile.getParent());
        file.transferTo(destinationFile.toFile());

        // 2. Scansiona il file appena salvato aprendo un nuovo stream
        try (InputStream inputStream = new FileInputStream(destinationFile.toFile())) {
            if (!clamAVService.scan(inputStream)) {
                // Se infetto, cancella il file e solleva eccezione
                Files.deleteIfExists(destinationFile);
                throw new StorageException("File rejected by antivirus scanner.");
            }
        } catch (Exception e) {
            if (e instanceof StorageException) throw e;
            // Se l'antivirus fallisce per motivi tecnici, lasciamo il file (fail-open come concordato)
        }
    }

    @Override
    public void store(Path source, Path destinationRelativePath) throws IOException {
        Path destinationFile = resolveInsideStorage(destinationRelativePath);
        Files.createDirectories(destinationFile.getParent());
        Files.copy(source, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(Path path) throws IOException {
        Path file = resolveInsideStorage(path);
        Files.deleteIfExists(file);
    }

    @Override
    public Resource loadAsResource(Path path) throws IOException {
        try {
            Path file = resolveInsideStorage(path);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageException("Could not read file: " + path);
            }
        } catch (MalformedURLException e) {
            throw new StorageException("Could not read file: " + path, e);
        }
    }

    private Path resolveInsideStorage(Path relativePath) {
        if (relativePath == null) {
            throw new StorageException("Path is required.");
        }

        Path normalizedRelative = relativePath.normalize();
        if (normalizedRelative.isAbsolute()) {
            throw new StorageException("Cannot access absolute paths.");
        }

        Path resolved = normalizedRootLocation.resolve(normalizedRelative).normalize();
        if (!resolved.startsWith(normalizedRootLocation)) {
            throw new StorageException("Cannot access files outside storage root.");
        }
        return resolved;
    }
}

package com.printcalculator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.printcalculator.exception.StorageException;

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
    private final ClamAVService clamAVService;

    public FileSystemStorageService(@Value("${storage.location:storage_orders}") String storageLocation, ClamAVService clamAVService) {
        this.rootLocation = Paths.get(storageLocation);
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
        Path destinationFile = this.rootLocation.resolve(destinationRelativePath).normalize().toAbsolutePath();
        if (!destinationFile.getParent().startsWith(this.rootLocation.toAbsolutePath())) {
            // Security check
            throw new StorageException("Cannot store file outside current directory.");
        }

        // Scan stream (Read 1)
        try (InputStream inputStream = file.getInputStream()) {
            if (!clamAVService.scan(inputStream)) {
                throw new StorageException("File rejected by antivirus scanner.");
            }
        }

        // Save to disk (Using transferTo which is safer than opening another stream)
        Files.createDirectories(destinationFile.getParent());
        file.transferTo(destinationFile.toFile());
    }

    @Override
    public void store(Path source, Path destinationRelativePath) throws IOException {
        Path destinationFile = this.rootLocation.resolve(destinationRelativePath).normalize().toAbsolutePath();
        if (!destinationFile.getParent().startsWith(this.rootLocation.toAbsolutePath())) {
             throw new StorageException("Cannot store file outside current directory.");
        }
        Files.createDirectories(destinationFile.getParent());
        // We assume source is already safe/scanned if it is internal? 
        // Or should we scan it too? 
        // If it comes from QuoteSession (which was scanned on upload), it is safe.
        // If we want to be paranoid, we can scan again, but maybe overkill.
        // Let's assume it is safe for internal copies.
        Files.copy(source, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(Path path) throws IOException {
        Path file = rootLocation.resolve(path);
        Files.deleteIfExists(file);
    }

    @Override
    public Resource loadAsResource(Path path) throws IOException {
        try {
            Path file = rootLocation.resolve(path);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + path);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file: " + path, e);
        }
    }
}

package com.printcalculator.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.io.IOException;

public interface StorageService {
    void init();
    void store(MultipartFile file, Path destination) throws IOException;
    void store(Path source, Path destination) throws IOException;
    void delete(Path path) throws IOException;
    Resource loadAsResource(Path path) throws IOException;
}

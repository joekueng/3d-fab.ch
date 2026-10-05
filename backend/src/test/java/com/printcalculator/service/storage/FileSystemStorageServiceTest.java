package com.printcalculator.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@ExtendWith(MockitoExtension.class)
class FileSystemStorageServiceTest {
    @TempDir Path storageRoot;
    @Mock ClamAVService scanner;

    @Test
    void scannerFailureDoesNotStoreUploadedFile() throws Exception {
        var storage = new FileSystemStorageService(storageRoot.toString(), scanner);
        var file = new MockMultipartFile("file", "model.stl", "model/stl", "solid model".getBytes());
        Path relativePath = Path.of("orders", "model.stl");
        when(scanner.scan(any())).thenThrow(new ResponseStatusException(SERVICE_UNAVAILABLE, "ANTIVIRUS_UNAVAILABLE"));

        assertThrows(ResponseStatusException.class, () -> storage.store(file, relativePath));

        assertFalse(Files.exists(storageRoot.resolve(relativePath)));
    }
}

package com.printcalculator.service;

import com.printcalculator.service.quote.QuoteStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelPreviewServiceTest {
    @Mock SlicerService slicer;
    @Mock ClamAVService scanner;
    private ModelPreviewService service;

    @BeforeEach
    void setUp() {
        service = new ModelPreviewService(slicer, new SlicingCapacityService(1, 0, 0), scanner, new QuoteStorageService());
    }

    @Test
    void returnsGeneratedStlAndCleansTemporaryFiles() throws Exception {
        byte[] stl = "solid preview\nendsolid preview".getBytes(StandardCharsets.UTF_8);
        var original = new MockMultipartFile("file", "model.3mf", "application/octet-stream", new byte[]{1, 2, 3});
        var source = new AtomicReference<Path>();
        when(scanner.scan(any(InputStream.class))).thenReturn(true);
        when(slicer.convertModelToPersistentStl(any(File.class), any(Path.class), eq("mm"))).thenAnswer(invocation -> {
            source.set(invocation.getArgument(0, File.class).toPath());
            assertArrayEquals(original.getBytes(), Files.readAllBytes(source.get()));
            Path output = invocation.getArgument(1, Path.class);
            Files.write(output, stl);
            return output;
        });

        assertArrayEquals(stl, service.createStlPreview(original));
        assertFalse(Files.exists(source.get().getParent()));
        assertArrayEquals(new byte[]{1, 2, 3}, original.getBytes());
    }

    @Test
    void scannerFailureBlocksConversion() throws Exception {
        var file = new MockMultipartFile("file", "model.obj", "text/plain", new byte[]{1});
        when(scanner.scan(any(InputStream.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ResponseStatusException.class, () -> service.createStlPreview(file));
        verifyNoInteractions(slicer);
    }

    @Test
    void cleansTemporaryFilesAfterConversionFailure() throws Exception {
        var file = new MockMultipartFile("file", "model.obj", "text/plain", new byte[]{1});
        var source = new AtomicReference<Path>();
        when(scanner.scan(any(InputStream.class))).thenReturn(true);
        when(slicer.convertModelToPersistentStl(any(File.class), any(Path.class), eq("mm"))).thenAnswer(invocation -> {
            source.set(invocation.getArgument(0, File.class).toPath());
            throw new java.io.IOException("conversion failed");
        });

        assertThrows(java.io.IOException.class, () -> service.createStlPreview(file));
        assertFalse(Files.exists(source.get().getParent()));
    }

    @Test
    void unsupportedFormatsAreRejectedBeforeScanning() {
        var file = new MockMultipartFile("file", "model.exe", "application/octet-stream", new byte[]{1});
        var error = assertThrows(ResponseStatusException.class, () -> service.createStlPreview(file));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(scanner, slicer);
    }
}

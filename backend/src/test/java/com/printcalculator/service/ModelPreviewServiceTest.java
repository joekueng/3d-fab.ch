package com.printcalculator.service;

import com.printcalculator.service.quote.QuoteStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"obj", "step", "stp", "3mf"})
    void scansBeforeConvertingAndForwardsTheSelectedUnit(String extension) throws Exception {
        var file = new MockMultipartFile("file", "model." + extension, "application/octet-stream", new byte[]{1});
        when(slicer.convertModelToPersistentStl(any(File.class), any(Path.class), eq("in")))
                .thenAnswer(invocation -> Files.write(invocation.getArgument(1, Path.class), new byte[]{2, 3}));
        assertArrayEquals(new byte[]{2, 3}, service.createStlPreview(file, "in"));
        var order = inOrder(scanner, slicer);
        order.verify(scanner).scan(any(InputStream.class));
        order.verify(slicer).convertModelToPersistentStl(any(File.class), any(Path.class), eq("in"));
    }

    @Test
    void stlPreviewPreservesBytesWithoutConversion() throws Exception {
        byte[] bytes = "solid original\nendsolid original".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "model.STL", "model/stl", bytes);
        assertArrayEquals(bytes, service.createStlPreview(file));
        verify(scanner).scan(any(InputStream.class));
        verifyNoInteractions(slicer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "feet", "MM", "../mm"})
    void invalidUnitsAreRejectedBeforeScanning(String unit) {
        var file = new MockMultipartFile("file", "model.obj", "text/plain", new byte[]{1});
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.createStlPreview(file, unit)).getStatusCode());
        verifyNoInteractions(scanner, slicer);
    }

    @Test
    void rejectsOversizedInputWithoutReadingIt() {
        var file = spy(new MockMultipartFile("file", "model.obj", "text/plain", new byte[]{1}));
        doReturn(200L * 1024 * 1024 + 1).when(file).getSize();
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(ResponseStatusException.class,
                () -> service.createStlPreview(file)).getStatusCode());
        verifyNoInteractions(scanner, slicer);
    }

    @Test
    void scannerFailureReleasesCapacityForTheNextUpload() throws Exception {
        var file = new MockMultipartFile("file", "model.stl", "model/stl", new byte[]{1});
        when(scanner.scan(any(InputStream.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.createStlPreview(file));
        assertArrayEquals(new byte[]{1}, service.createStlPreview(file));
    }

    @Test
    void fullCapacityRejectsPreviewBeforeScanning() {
        var capacity = new SlicingCapacityService(1, 0, 0);
        var limited = new ModelPreviewService(slicer, capacity, scanner, new QuoteStorageService());
        var file = new MockMultipartFile("file", "model.stl", "model/stl", new byte[]{1});
        try (var lease = capacity.acquire()) {
            var failure = assertThrows(ResponseStatusException.class, () -> limited.createStlPreview(file));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.getStatusCode());
            assertEquals("5", failure.getHeaders().getFirst("Retry-After"));
            verifyNoInteractions(scanner, slicer);
        }
    }

    @Test
    void oversizedConvertedPreviewIsRejectedAndRemoved() throws Exception {
        var file = new MockMultipartFile("file", "model.step", "application/octet-stream", new byte[]{1});
        var output = new AtomicReference<Path>();
        when(slicer.convertModelToPersistentStl(any(File.class), any(Path.class), eq("mm")))
                .thenAnswer(invocation -> {
                    output.set(invocation.getArgument(1, Path.class));
                    try (var channel = java.nio.channels.FileChannel.open(output.get(),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
                        channel.position(75L * 1024 * 1024);
                        channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
                    }
                    return output.get();
                });
        var error = assertThrows(com.printcalculator.exception.ModelProcessingException.class,
                () -> service.createStlPreview(file));
        assertEquals("MODEL_PREVIEW_UNAVAILABLE", error.getCode());
        assertFalse(Files.exists(output.get().getParent()));
    }
}

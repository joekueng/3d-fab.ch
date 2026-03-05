package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomQuoteRequestAttachmentServiceTest {

    @Mock
    private CustomQuoteRequestAttachmentRepository attachmentRepo;
    @Mock
    private ClamAVService clamAVService;

    @InjectMocks
    private CustomQuoteRequestAttachmentService service;

    private UUID lastRequestIdForCleanup;

    @AfterEach
    void cleanStorageDirectory() {
        if (lastRequestIdForCleanup == null) {
            return;
        }
        Path requestDir = Paths.get("storage_requests", "quote-requests", lastRequestIdForCleanup.toString());
        if (!Files.exists(requestDir)) {
            return;
        }
        try (var walk = Files.walk(requestDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void storeAttachments_withNullFiles_shouldReturnZero() throws Exception {
        CustomQuoteRequest request = buildRequest();

        int count = service.storeAttachments(request, null);

        assertEquals(0, count);
        verifyNoInteractions(clamAVService, attachmentRepo);
    }

    @Test
    void storeAttachments_withTooManyFiles_shouldThrowIOException() {
        CustomQuoteRequest request = buildRequest();
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            files.add(new MockMultipartFile("files", "file-" + i + ".stl", "model/stl", "solid".getBytes(StandardCharsets.UTF_8)));
        }

        IOException ex = assertThrows(
                IOException.class,
                () -> service.storeAttachments(request, new ArrayList<>(files))
        );

        assertTrue(ex.getMessage().contains("Too many files"));
        verifyNoInteractions(clamAVService, attachmentRepo);
    }

    @Test
    void storeAttachments_withCompressedFile_shouldThrowBadRequest() {
        CustomQuoteRequest request = buildRequest();
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "archive.zip",
                "application/zip",
                "dummy".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.storeAttachments(request, List.of(file))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(clamAVService, attachmentRepo);
    }

    @Test
    void storeAttachments_withValidFile_shouldScanPersistAndWriteOnDisk() throws Exception {
        CustomQuoteRequest request = buildRequest();
        lastRequestIdForCleanup = request.getId();

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "part.stl",
                "model/stl",
                "solid model".getBytes(StandardCharsets.UTF_8)
        );

        when(clamAVService.scan(any())).thenReturn(true);
        when(attachmentRepo.save(any(CustomQuoteRequestAttachment.class))).thenAnswer(invocation -> {
            CustomQuoteRequestAttachment attachment = invocation.getArgument(0);
            if (attachment.getId() == null) {
                attachment.setId(UUID.randomUUID());
            }
            return attachment;
        });

        int savedCount = service.storeAttachments(request, List.of(file));

        assertEquals(1, savedCount);

        ArgumentCaptor<CustomQuoteRequestAttachment> captor = ArgumentCaptor.forClass(CustomQuoteRequestAttachment.class);
        verify(attachmentRepo, times(2)).save(captor.capture());
        verify(clamAVService, times(1)).scan(any());

        CustomQuoteRequestAttachment persisted = captor.getAllValues().get(1);
        Path absolutePath = Paths.get("storage_requests").toAbsolutePath().normalize()
                .resolve(persisted.getStoredRelativePath())
                .normalize();

        assertTrue(Files.exists(absolutePath));
        assertEquals("solid model", Files.readString(absolutePath, StandardCharsets.UTF_8));
    }

    private CustomQuoteRequest buildRequest() {
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(UUID.randomUUID());
        return request;
    }
}

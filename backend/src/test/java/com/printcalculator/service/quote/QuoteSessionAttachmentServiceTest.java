package com.printcalculator.service.quote;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderDeliverableFile;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.QuoteSessionAttachment;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.repository.OrderDeliverableFileRepository;
import com.printcalculator.repository.QuoteSessionAttachmentRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.storage.ClamAVService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSessionAttachmentServiceTest {

    @Mock
    private QuoteSessionRepository sessionRepo;
    @Mock
    private QuoteSessionAttachmentRepository attachmentRepo;
    @Mock
    private OrderDeliverableFileRepository deliverableRepo;
    @Mock
    private ClamAVService antivirus;
    @Mock
    private StorageService storageService;

    @TempDir
    Path tempDir;

    private QuoteSessionAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new QuoteSessionAttachmentService(
                sessionRepo,
                attachmentRepo,
                deliverableRepo,
                antivirus,
                storageService,
                tempDir.toString(),
                tempDir.resolve("orders").toString(),
                50L * 1024 * 1024,
                15
        );
    }

    @Test
    void upload_scansAndStoresImageAttachment() throws Exception {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.countByQuoteSessionId(session.getId())).thenReturn(0L);
        when(attachmentRepo.saveAndFlush(any(QuoteSessionAttachment.class))).thenAnswer(invocation -> {
            QuoteSessionAttachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            return attachment;
        });
        when(attachmentRepo.save(any(QuoteSessionAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(antivirus.scanRequired(any())).thenReturn(true);

        var results = service.upload(session.getId(), List.of(pngFile("photo.png")));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().isImage());
        assertEquals("image/png", results.getFirst().getMimeType());
        QuoteSessionAttachment stored = captureSaved();
        assertNotNull(stored.getSha256Hex());
        assertTrue(stored.getStoredRelativePath().startsWith(session.getId() + "/attachments/"));
        assertTrue(Files.isRegularFile(tempDir.resolve(stored.getStoredRelativePath())));
    }

    @Test
    void upload_rejectsWhenSessionConverted() {
        QuoteSession session = session(UUID.randomUUID(), "CONVERTED");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(session.getId(), List.of(pngFile("photo.png")))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void upload_rejectsTooManyFilesPerInvoice() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.countByQuoteSessionId(session.getId())).thenReturn(15L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(session.getId(), List.of(pngFile("photo.png")))
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
    }

    @Test
    void upload_rejectsOversizedFile() {
        QuoteSessionAttachmentService smallLimitService = new QuoteSessionAttachmentService(
                sessionRepo, attachmentRepo, deliverableRepo, antivirus, storageService,
                tempDir.toString(), tempDir.resolve("orders").toString(), 10, 15);
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> smallLimitService.upload(session.getId(), List.of(pngFile("photo.png")))
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
    }

    @Test
    void upload_failsClosedWhenAntivirusUnavailable() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.countByQuoteSessionId(session.getId())).thenReturn(0L);
        when(attachmentRepo.saveAndFlush(any(QuoteSessionAttachment.class))).thenAnswer(invocation -> {
            QuoteSessionAttachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            return attachment;
        });
        when(antivirus.scanRequired(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ANTIVIRUS_UNAVAILABLE"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(session.getId(), List.of(pngFile("photo.png")))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void upload_rejectsImageWithMismatchedContent() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.countByQuoteSessionId(session.getId())).thenReturn(0L);

        MockMultipartFile fake = new MockMultipartFile(
                "files", "photo.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload(session.getId(), List.of(fake))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void delete_removesMetadataAndFile() throws Exception {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        UUID attachmentId = UUID.randomUUID();
        String relative = session.getId() + "/attachments/" + attachmentId + "/model.stl";
        Path stored = tempDir.resolve(relative);
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "solid");
        QuoteSessionAttachment attachment = new QuoteSessionAttachment();
        attachment.setId(attachmentId);
        attachment.setQuoteSession(session);
        attachment.setOriginalFilename("model.stl");
        attachment.setStoredRelativePath(relative);
        attachment.setMimeType("model/stl");

        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.findByIdAndQuoteSession_Id(attachmentId, session.getId()))
                .thenReturn(Optional.of(attachment));

        service.delete(session.getId(), attachmentId);

        assertFalse(Files.exists(stored));
        verify(attachmentRepo).delete(attachment);
    }

    @Test
    void delete_rejectsAttachmentOfAnotherSession() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        UUID attachmentId = UUID.randomUUID();
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(attachmentRepo.findByIdAndQuoteSession_Id(attachmentId, session.getId()))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(session.getId(), attachmentId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void loadPreview_rejectsNonImageAttachment() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        UUID attachmentId = UUID.randomUUID();
        QuoteSessionAttachment attachment = new QuoteSessionAttachment();
        attachment.setId(attachmentId);
        attachment.setMimeType("application/pdf");
        when(attachmentRepo.findByIdAndQuoteSession_Id(attachmentId, session.getId()))
                .thenReturn(Optional.of(attachment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.loadPreview(session.getId(), attachmentId)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void snapshotToOrder_copiesAttachmentsIntoDeliverables() throws Exception {
        QuoteSession session = session(UUID.randomUUID(), "CONVERTED");
        UUID attachmentId = UUID.randomUUID();
        String relative = session.getId() + "/attachments/" + attachmentId + "/photo.png";
        Path stored = tempDir.resolve(relative);
        Files.createDirectories(stored.getParent());
        Files.write(stored, pngBytes());
        QuoteSessionAttachment attachment = new QuoteSessionAttachment();
        attachment.setId(attachmentId);
        attachment.setQuoteSession(session);
        attachment.setOriginalFilename("photo.png");
        attachment.setStoredRelativePath(relative);
        attachment.setMimeType("image/png");
        attachment.setFileSizeBytes(Files.size(stored));

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setIsCadOrder(true);
        order.setSourceQuoteSession(session);

        when(attachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId()))
                .thenReturn(List.of(attachment));
        when(deliverableRepo.save(any(OrderDeliverableFile.class))).thenAnswer(invocation -> {
            OrderDeliverableFile file = invocation.getArgument(0);
            if (file.getId() == null) {
                file.setId(UUID.randomUUID());
            }
            return file;
        });

        service.snapshotToOrder(new OrderCreatedEvent(this, order));

        ArgumentCaptor<OrderDeliverableFile> captor = ArgumentCaptor.forClass(OrderDeliverableFile.class);
        verify(deliverableRepo, atLeastOnce()).save(captor.capture());
        OrderDeliverableFile saved = captor.getAllValues().getLast();
        assertEquals("photo.png", saved.getOriginalFilename());
        assertEquals("image/png", saved.getMimeType());
        assertTrue(saved.getStoredRelativePath().startsWith(
                "orders/" + order.getId() + "/cad-deliverables/" + saved.getId()));
        verify(storageService).store(any(Path.class), any(Path.class));
    }

    private QuoteSessionAttachment captureSaved() {
        ArgumentCaptor<QuoteSessionAttachment> captor = ArgumentCaptor.forClass(QuoteSessionAttachment.class);
        verify(attachmentRepo, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private QuoteSession session(UUID id, String status) {
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus(status);
        return session;
    }

    private MockMultipartFile pngFile(String name) {
        return new MockMultipartFile("files", name, "image/png", pngBytes());
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0};
    }
}

package com.printcalculator.service.admin;

import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.quote.QuoteStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionPreviewServiceTest {
    private final UUID sessionId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final QuoteLineItemRepository items = mock(QuoteLineItemRepository.class);
    @TempDir
    Path temp;

    private QuoteLineItem item() {
        var session = new QuoteSession();
        session.setId(sessionId);
        var item = new QuoteLineItem();
        item.setQuoteSession(session);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        return item;
    }

    @Test
    void servesStoredConvertedStlWithPrivateHeaders() throws Exception {
        var item = item();
        item.setStoredPath(sessionId + "/original.obj");
        item.setPricingBreakdown(Map.of("convertedStoredPath", sessionId + "/preview.stl"));
        Path stl = Files.writeString(temp.resolve("preview.stl"), "solid preview\nendsolid preview");
        var storage = spy(new QuoteStorageService());
        doReturn(stl).when(storage).resolveStoredQuotePath(sessionId + "/preview.stl", sessionId);
        var response = new AdminSessionPreviewService(items, storage).preview(sessionId, itemId);
        assertEquals("model/stl", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertArrayEquals(Files.readAllBytes(stl), response.getBody().getContentAsByteArray());
    }

    @Test
    void rejectsItemFromAnotherSessionBeforeReadingStorage() {
        item();
        var storage = mock(QuoteStorageService.class);
        var error = assertThrows(ResponseStatusException.class,
                () -> new AdminSessionPreviewService(items, storage).preview(UUID.randomUUID(), itemId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsPathsOutsideTheRequestedSession() {
        var item = item();
        item.setStoredPath(UUID.randomUUID() + "/model.stl");
        var service = new AdminSessionPreviewService(items, new QuoteStorageService());
        var error = assertThrows(ResponseStatusException.class, () -> service.preview(sessionId, itemId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void doesNotServeOriginalNonStlAsAPreview() throws Exception {
        var item = item();
        item.setStoredPath(sessionId + "/original.obj");
        Path original = Files.writeString(temp.resolve("original.obj"), "v 1 2 3");
        var storage = spy(new QuoteStorageService());
        doReturn(original).when(storage).resolveStoredQuotePath(item.getStoredPath(), sessionId);
        var service = new AdminSessionPreviewService(items, storage);
        var error = assertThrows(ResponseStatusException.class, () -> service.preview(sessionId, itemId));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}

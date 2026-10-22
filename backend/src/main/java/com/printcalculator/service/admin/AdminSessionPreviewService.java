package com.printcalculator.service.admin;

import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.quote.QuoteStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AdminSessionPreviewService {
    private final QuoteLineItemRepository items;
    private final QuoteStorageService storage;

    public AdminSessionPreviewService(QuoteLineItemRepository items, QuoteStorageService storage) {
        this.items = items;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> preview(UUID sessionId, UUID itemId) throws IOException {
        var item = items.findById(itemId)
                .filter(value -> value.getQuoteSession().getId().equals(sessionId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session item not found"));
        String converted = storage.extractConvertedStoredPath(item);
        Path path = storage.resolveStoredQuotePath(converted != null ? converted : item.getStoredPath(), sessionId);
        if (path == null || !Files.isRegularFile(path)
                || !"stl".equals(storage.getSafeExtension(path.getFileName().toString(), ""))) {
            throw new ResponseStatusException(NOT_FOUND, "STL preview not available");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("model/stl"))
                .contentLength(Files.size(path))
                .body(new UrlResource(path.toUri()));
    }
}

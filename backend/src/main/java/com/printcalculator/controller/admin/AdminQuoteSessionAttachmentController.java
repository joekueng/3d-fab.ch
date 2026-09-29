package com.printcalculator.controller.admin;

import com.printcalculator.dto.QuoteSessionAttachmentDto;
import com.printcalculator.service.quote.QuoteSessionAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quote-sessions")
public class AdminQuoteSessionAttachmentController {

    private final QuoteSessionAttachmentService attachmentService;

    public AdminQuoteSessionAttachmentController(QuoteSessionAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{sessionId}/attachments")
    public ResponseEntity<List<QuoteSessionAttachmentDto>> listAttachments(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(attachmentService.list(sessionId));
    }

    @GetMapping("/{sessionId}/attachments/{attachmentId}/preview")
    public ResponseEntity<Resource> previewAttachment(@PathVariable UUID sessionId,
                                                      @PathVariable UUID attachmentId) {
        QuoteSessionAttachmentService.AttachmentPreview preview =
                attachmentService.loadPreview(sessionId, attachmentId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(preview.mimeType()))
                .contentLength(preview.sizeBytes())
                .body(preview.resource());
    }

    @PostMapping(value = "/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<QuoteSessionAttachmentDto>> uploadAttachments(
            @PathVariable UUID sessionId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(attachmentService.upload(sessionId, files));
    }

    @DeleteMapping("/{sessionId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID sessionId,
            @PathVariable UUID attachmentId
    ) {
        attachmentService.delete(sessionId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}

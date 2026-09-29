package com.printcalculator.controller.admin;

import com.printcalculator.dto.QuoteSessionAttachmentDto;
import com.printcalculator.service.quote.QuoteSessionAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<List<QuoteSessionAttachmentDto>> uploadAttachments(
            @PathVariable UUID sessionId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(attachmentService.upload(sessionId, files));
    }

    @DeleteMapping("/{sessionId}/attachments/{attachmentId}")
    @Transactional
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID sessionId,
            @PathVariable UUID attachmentId
    ) {
        attachmentService.delete(sessionId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}

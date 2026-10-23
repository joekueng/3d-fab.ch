package com.printcalculator.controller.admin;

import com.printcalculator.service.admin.AdminSessionPreviewService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionPreviewController {
    private final AdminSessionPreviewService previews;

    public AdminSessionPreviewController(AdminSessionPreviewService previews) {
        this.previews = previews;
    }

    @GetMapping("/{sessionId}/items/{itemId}/stl-preview")
    public ResponseEntity<Resource> preview(@PathVariable UUID sessionId, @PathVariable UUID itemId) throws IOException {
        return previews.preview(sessionId, itemId);
    }
}

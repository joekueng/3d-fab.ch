package com.printcalculator.controller;

import com.printcalculator.service.ModelPreviewService;
import com.printcalculator.service.QuoteRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class ModelPreviewController {
    private final ModelPreviewService modelPreviewService;
    private final QuoteRateLimitService quoteRateLimitService;

    public ModelPreviewController(ModelPreviewService modelPreviewService, QuoteRateLimitService quoteRateLimitService) {
        this.modelPreviewService = modelPreviewService;
        this.quoteRateLimitService = quoteRateLimitService;
    }

    @PostMapping(value = "/api/model-previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> createPreview(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "modelUnit", defaultValue = "mm") String modelUnit,
                                               HttpServletRequest request) throws IOException {
        quoteRateLimitService.checkSlicingAllowed(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType("model/stl"))
                .body(modelPreviewService.createStlPreview(file, modelUnit));
    }
}

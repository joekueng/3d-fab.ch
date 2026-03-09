package com.printcalculator.controller;

import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.service.request.CustomQuoteRequestControllerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-quote-requests")
public class CustomQuoteRequestController {

    private final CustomQuoteRequestControllerService customQuoteRequestControllerService;

    public CustomQuoteRequestController(CustomQuoteRequestControllerService customQuoteRequestControllerService) {
        this.customQuoteRequestControllerService = customQuoteRequestControllerService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<CustomQuoteRequest> createCustomQuoteRequest(
            @Valid @RequestPart("request") QuoteRequestDto requestDto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        return ResponseEntity.ok(customQuoteRequestControllerService.createCustomQuoteRequest(requestDto, files));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomQuoteRequest> getCustomQuoteRequest(@PathVariable UUID id) {
        return customQuoteRequestControllerService.getCustomQuoteRequest(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

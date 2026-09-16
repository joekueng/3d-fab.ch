package com.printcalculator.controller;

import com.printcalculator.dto.QuoteSessionEmailRequest;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.quote.QuoteSessionEmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/quote-sessions")
public class QuoteSessionEmailController {
    private final QuoteSessionEmailService service;
    private final QuoteRateLimitService limits;
    public QuoteSessionEmailController(QuoteSessionEmailService service, QuoteRateLimitService limits) {
        this.service = service;
        this.limits = limits;
    }
    @PostMapping("/{id}/resume")
    public ResponseEntity<com.printcalculator.dto.InformationDto.Credential> resume(@PathVariable UUID id,
            HttpServletRequest request) {
        limits.checkAllowed(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .body(service.resume(id));
    }

    @PutMapping("/{id}/information")
    public ResponseEntity<Void> linkInformation(@PathVariable UUID id,
            @Valid @RequestBody com.printcalculator.dto.InformationDto.DraftLink payload, HttpServletRequest request) {
        limits.checkAllowed(request);
        service.linkInformation(id, payload);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    public record LinkResponse(String url) {}

    @PostMapping("/{id}/link")
    public ResponseEntity<LinkResponse> link(@PathVariable UUID id,
            @Valid @RequestBody com.printcalculator.dto.QuoteSessionLinkRequest payload, HttpServletRequest request) {
        limits.checkAllowed(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer")
                .body(new LinkResponse(service.createLink(id, payload)));
    }

    @PostMapping("/{id}/email")
    public ResponseEntity<Void> send(@PathVariable UUID id, @Valid @RequestBody QuoteSessionEmailRequest payload,
            HttpServletRequest request) {
        limits.checkAllowed(request);
        service.send(id, payload);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}

package com.printcalculator.controller;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.service.information.OrderInformationService;
import com.printcalculator.service.QuoteRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/orders/{id}/information")
public class CustomerOrderInformationController {
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    private final OrderInformationService service;
    private final QuoteRateLimitService limits;
    public CustomerOrderInformationController(OrderInformationService service, QuoteRateLimitService limits) { this.service = service; this.limits = limits; }
    @PostMapping("/resume")
    public InformationDto.Credential resume(@PathVariable UUID id, HttpServletRequest request) {
        limits.checkAllowed(request);
        return service.resumeOrder(id);
    }
    @GetMapping public InformationDto get(@PathVariable UUID id, @RequestHeader("X-Information-Token") String token) { return service.getOrder(id, token, false); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InformationDto append(@PathVariable UUID id, @RequestHeader("X-Information-Token") String token,
            @Valid @RequestPart("entry") InformationDto.EntryRequest entry,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, HttpServletRequest request) throws IOException {
        limits.checkAllowed(request); return service.append(id, token, entry, files);
    }
    @GetMapping("/files/{file}") public ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable UUID file,
            @RequestHeader("X-Information-Token") String token) throws IOException { return service.download(id, token, file, true, false); }
}

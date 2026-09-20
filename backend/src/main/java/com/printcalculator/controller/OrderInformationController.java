package com.printcalculator.controller;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.service.information.OrderInformationService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/information-drafts")
public class OrderInformationController {
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    private final OrderInformationService service;
    public OrderInformationController(OrderInformationService service) { this.service = service; }
    @PostMapping public InformationDto.Credential create() {
        return service.create();
    }
    @GetMapping("/{id}") public InformationDto get(@PathVariable UUID id, @RequestHeader("X-Information-Token") String token) { return service.getDraft(id, token); }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InformationDto save(@PathVariable UUID id, @RequestHeader("X-Information-Token") String token,
            @Valid @RequestPart("entry") InformationDto.EntryRequest entry,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        return service.saveDraft(id, token, entry, files);
    }
    @GetMapping("/{id}/files/{file}") public ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable UUID file,
            @RequestHeader("X-Information-Token") String token) throws IOException { return service.download(id, token, file, false, false); }
}

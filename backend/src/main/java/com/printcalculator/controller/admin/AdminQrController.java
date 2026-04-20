package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminQrLinkDto;
import com.printcalculator.dto.AdminQrOverviewStatsDto;
import com.printcalculator.dto.AdminQrLinkStatsDto;
import com.printcalculator.dto.AdminUpsertQrLinkRequest;
import com.printcalculator.service.admin.AdminQrControllerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/qr-links")
@Transactional(readOnly = true)
public class AdminQrController {
    private final AdminQrControllerService adminQrControllerService;

    public AdminQrController(AdminQrControllerService adminQrControllerService) {
        this.adminQrControllerService = adminQrControllerService;
    }

    @GetMapping
    public ResponseEntity<List<AdminQrLinkDto>> listQrLinks() {
        return ResponseEntity.ok(adminQrControllerService.listQrLinks());
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminQrOverviewStatsDto> getOverviewStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(adminQrControllerService.getOverviewStats(from, to));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminQrLinkDto> createQrLink(@RequestBody AdminUpsertQrLinkRequest payload) {
        return ResponseEntity.ok(adminQrControllerService.createQrLink(payload));
    }

    @PatchMapping("/{qrLinkId}")
    @Transactional
    public ResponseEntity<AdminQrLinkDto> updateQrLink(@PathVariable UUID qrLinkId,
                                                       @RequestBody AdminUpsertQrLinkRequest payload) {
        return ResponseEntity.ok(adminQrControllerService.updateQrLink(qrLinkId, payload));
    }

    @GetMapping("/{qrLinkId}/stats")
    public ResponseEntity<AdminQrLinkStatsDto> getQrLinkStats(
            @PathVariable UUID qrLinkId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(adminQrControllerService.getQrLinkStats(qrLinkId, from, to));
    }

    @GetMapping(value = "/{qrLinkId}/svg", produces = "image/svg+xml")
    public ResponseEntity<String> downloadQrSvg(@PathVariable UUID qrLinkId) {
        String filename = adminQrControllerService.generateQrSvgFilename(qrLinkId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(adminQrControllerService.generateQrSvg(qrLinkId));
    }
}

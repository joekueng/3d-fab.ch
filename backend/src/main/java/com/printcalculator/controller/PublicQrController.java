package com.printcalculator.controller;

import com.printcalculator.service.qr.PublicQrControllerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/public/qr")
public class PublicQrController {
    private final PublicQrControllerService publicQrControllerService;

    public PublicQrController(PublicQrControllerService publicQrControllerService) {
        this.publicQrControllerService = publicQrControllerService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Void> resolveQr(@PathVariable String slug, HttpServletRequest request) {
        var redirect = publicQrControllerService.resolveRedirect(slug, request);
        return ResponseEntity.status(302)
                .location(URI.create(redirect.finalPath()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, "Accept-Language, User-Agent")
                .build();
    }
}

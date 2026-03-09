package com.printcalculator.controller;

import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/media")
@Transactional(readOnly = true)
public class PublicMediaController {

    private final PublicMediaQueryService publicMediaQueryService;

    public PublicMediaController(PublicMediaQueryService publicMediaQueryService) {
        this.publicMediaQueryService = publicMediaQueryService;
    }

    @GetMapping("/usages")
    public ResponseEntity<List<PublicMediaUsageDto>> getUsageMedia(@RequestParam String usageType,
                                                                   @RequestParam String usageKey,
                                                                   @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicMediaQueryService.getUsageMedia(usageType, usageKey, lang));
    }
}

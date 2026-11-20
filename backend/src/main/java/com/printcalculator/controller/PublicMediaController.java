package com.printcalculator.controller;

import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/media")
@Transactional(readOnly = true)
public class PublicMediaController {

    private static final int MAX_USAGE_KEYS = 50;
    private final PublicMediaQueryService publicMediaQueryService;

    public PublicMediaController(PublicMediaQueryService publicMediaQueryService) {
        this.publicMediaQueryService = publicMediaQueryService;
    }

    @GetMapping(value = "/usages", params = "!usageKeys")
    public ResponseEntity<List<PublicMediaUsageDto>> getUsageMedia(@RequestParam String usageType,
                                                                   @RequestParam String usageKey,
                                                                   @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicMediaQueryService.getUsageMedia(usageType, usageKey, lang));
    }

    @GetMapping(value = "/usages", params = {"usageKeys", "!usageKey"})
    public Map<String, List<PublicMediaUsageDto>> getUsageMediaBatch(@RequestParam String usageType,
                                                                    @RequestParam MultiValueMap<String, String> params,
                                                                    @RequestParam(required = false) String lang) {
        List<String> usageKeys = params.getOrDefault("usageKeys", List.of());
        if (usageKeys.isEmpty() || usageKeys.size() > MAX_USAGE_KEYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide between 1 and 50 usageKeys.");
        }
        return publicMediaQueryService.getUsageMediaMap(usageType, usageKeys, lang);
    }
}

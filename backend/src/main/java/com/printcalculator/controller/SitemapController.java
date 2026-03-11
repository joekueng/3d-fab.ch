package com.printcalculator.controller;

import com.printcalculator.service.shop.ShopSitemapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class SitemapController {
    private final ShopSitemapService shopSitemapService;
    private final long cacheSeconds;

    public SitemapController(
            ShopSitemapService shopSitemapService,
            @Value("${app.sitemap.shop.cache-seconds:3600}") long cacheSeconds
    ) {
        this.shopSitemapService = shopSitemapService;
        this.cacheSeconds = Math.max(cacheSeconds, 0L);
    }

    @GetMapping(value = "/api/sitemap-shop.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getShopSitemap() {
        CacheControl cacheControl = cacheSeconds > 0
                ? CacheControl.maxAge(Duration.ofSeconds(cacheSeconds)).cachePublic()
                : CacheControl.noCache();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/xml;charset=UTF-8"))
                .cacheControl(cacheControl)
                .body(shopSitemapService.getShopSitemapXml());
    }
}

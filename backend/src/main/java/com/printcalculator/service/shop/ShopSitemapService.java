package com.printcalculator.service.shop;

import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShopSitemapService {
    private static final List<String> SUPPORTED_LANGUAGES = ShopProduct.SUPPORTED_LANGUAGES;
    private static final String DEFAULT_LANGUAGE = "it";
    private static final DateTimeFormatter LASTMOD_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Map<String, String> HREFLANG_BY_LANGUAGE = Map.of(
            "it", "it-CH",
            "en", "en-CH",
            "de", "de-CH",
            "fr", "fr-CH"
    );

    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopProductRepository shopProductRepository;
    private final String frontendBaseUrl;
    private final Duration cacheTtl;
    private final Clock clock;

    private volatile CachedSitemap cachedSitemap;

    @Autowired
    public ShopSitemapService(ShopCategoryRepository shopCategoryRepository,
                              ShopProductRepository shopProductRepository,
                              @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
                              @Value("${app.sitemap.shop.cache-seconds:3600}") long cacheSeconds) {
        this(shopCategoryRepository, shopProductRepository, frontendBaseUrl, cacheSeconds, Clock.systemUTC());
    }

    ShopSitemapService(ShopCategoryRepository shopCategoryRepository,
                       ShopProductRepository shopProductRepository,
                       String frontendBaseUrl,
                       long cacheSeconds,
                       Clock clock) {
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopProductRepository = shopProductRepository;
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
        this.cacheTtl = cacheSeconds > 0 ? Duration.ofSeconds(cacheSeconds) : Duration.ZERO;
        this.clock = clock;
    }

    public String getShopSitemapXml() {
        Instant now = Instant.now(clock);
        CachedSitemap current = cachedSitemap;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.xml();
        }

        synchronized (this) {
            current = cachedSitemap;
            now = Instant.now(clock);
            if (current != null && now.isBefore(current.expiresAt())) {
                return current.xml();
            }

            String xml = buildSitemapXml();
            Instant expiresAt = cacheTtl.isZero() ? now : now.plus(cacheTtl);
            cachedSitemap = new CachedSitemap(xml, expiresAt);
            return xml;
        }
    }

    private String buildSitemapXml() {
        List<ShopCategory> activeCategories = shopCategoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc();
        Set<UUID> activeCategoryIds = activeCategories.stream()
                .map(ShopCategory::getId)
                .collect(Collectors.toSet());

        List<ShopProduct> activeProducts = shopProductRepository.findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc();

        StringBuilder xml = new StringBuilder(16_384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" ");
        xml.append("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

        appendCategoryUrls(xml, activeCategories);
        appendProductUrls(xml, activeProducts, activeCategoryIds);

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendCategoryUrls(StringBuilder xml, List<ShopCategory> categories) {
        for (ShopCategory category : categories) {
            if (!Boolean.TRUE.equals(category.getIndexable())) {
                continue;
            }

            String encodedSlug = pathEncodeSegment(category.getSlug());
            Map<String, String> hrefByLanguage = new LinkedHashMap<>();
            for (String language : SUPPORTED_LANGUAGES) {
                hrefByLanguage.put(language, frontendBaseUrl + "/" + language + "/shop/" + encodedSlug);
            }

            appendUrlEntry(xml, hrefByLanguage, category.getUpdatedAt());
        }
    }

    private void appendProductUrls(StringBuilder xml,
                                   List<ShopProduct> products,
                                   Set<UUID> activeCategoryIds) {
        for (ShopProduct product : products) {
            if (!Boolean.TRUE.equals(product.getIndexable())) {
                continue;
            }
            if (product.getCategory() == null || !activeCategoryIds.contains(product.getCategory().getId())) {
                continue;
            }

            Map<String, String> hrefByLanguage = new LinkedHashMap<>();
            for (String language : SUPPORTED_LANGUAGES) {
                String publicSegment = ShopPublicPathSupport.buildProductPathSegment(product, language);
                hrefByLanguage.put(language, frontendBaseUrl + "/" + language + "/shop/p/" + pathEncodeSegment(publicSegment));
            }

            appendUrlEntry(xml, hrefByLanguage, product.getUpdatedAt());
        }
    }

    private void appendUrlEntry(StringBuilder xml,
                                Map<String, String> hrefByLanguage,
                                OffsetDateTime lastmod) {
        String defaultHref = hrefByLanguage.get(DEFAULT_LANGUAGE);
        if (defaultHref == null || defaultHref.isBlank()) {
            return;
        }

        for (String locLanguage : SUPPORTED_LANGUAGES) {
            String locHref = hrefByLanguage.get(locLanguage);
            if (locHref == null || locHref.isBlank()) {
                continue;
            }
            appendLocalizedUrlEntry(xml, locHref, hrefByLanguage, defaultHref, lastmod);
        }
    }

    private void appendLocalizedUrlEntry(StringBuilder xml,
                                         String locHref,
                                         Map<String, String> hrefByLanguage,
                                         String defaultHref,
                                         OffsetDateTime lastmod) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(xmlEscape(locHref)).append("</loc>\n");

        for (String language : SUPPORTED_LANGUAGES) {
            String href = hrefByLanguage.get(language);
            if (href == null || href.isBlank()) {
                continue;
            }
            xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"")
                    .append(HREFLANG_BY_LANGUAGE.getOrDefault(language, language))
                    .append("\" href=\"")
                    .append(xmlEscape(href))
                    .append("\" />\n");
        }

        xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"")
                .append(xmlEscape(defaultHref))
                .append("\" />\n");

        if (lastmod != null) {
            xml.append("    <lastmod>").append(LASTMOD_FORMATTER.format(lastmod)).append("</lastmod>\n");
        }

        xml.append("  </url>\n");
    }

    private String pathEncodeSegment(String rawSegment) {
        String safeSegment = rawSegment == null ? "" : rawSegment;
        return URLEncoder.encode(safeSegment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String xmlEscape(String value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = (baseUrl == null ? "" : baseUrl).trim();
        if (normalized.isBlank()) {
            return "http://localhost:4200";
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record CachedSitemap(String xml, Instant expiresAt) {
    }
}

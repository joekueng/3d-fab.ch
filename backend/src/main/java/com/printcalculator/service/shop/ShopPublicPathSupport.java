package com.printcalculator.service.shop;

import com.printcalculator.entity.ShopProduct;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class ShopPublicPathSupport {
    private static final String PRODUCT_ROUTE_PREFIX = "/shop/p/";

    private ShopPublicPathSupport() {
    }

    static String buildProductPathSegment(ShopProduct product, String language) {
        String localizedName = product.getNameForLanguage(language);
        String idPrefix = productIdPrefix(product.getId());
        String tail = firstNonBlank(slugify(localizedName), slugify(product.getSlug()), "product");
        return idPrefix.isBlank() ? tail : idPrefix + "-" + tail;
    }

    static Map<String, String> buildLocalizedProductPaths(ShopProduct product) {
        Map<String, String> localizedPaths = new LinkedHashMap<>();
        for (String language : ShopProduct.SUPPORTED_LANGUAGES) {
            localizedPaths.put(language, "/" + language + PRODUCT_ROUTE_PREFIX + buildProductPathSegment(product, language));
        }
        return localizedPaths;
    }

    static String productIdPrefix(UUID productId) {
        if (productId == null) {
            return "";
        }
        String raw = productId.toString().trim().toLowerCase(Locale.ROOT);
        int dashIndex = raw.indexOf('-');
        if (dashIndex > 0) {
            return raw.substring(0, dashIndex);
        }
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    static String slugify(String rawValue) {
        String safeValue = rawValue == null ? "" : rawValue;
        String normalized = Normalizer.normalize(safeValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
        return normalized;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

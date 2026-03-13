package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminShopCategoryDto;
import com.printcalculator.dto.AdminShopCategoryRefDto;
import com.printcalculator.dto.AdminUpsertShopCategoryRequest;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AdminShopCategoryControllerService {
    private static final String SHOP_CATEGORY_MEDIA_USAGE_TYPE = "SHOP_CATEGORY";
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH_PATTERN = Pattern.compile("(^-+|-+$)");

    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopProductRepository shopProductRepository;

    public AdminShopCategoryControllerService(ShopCategoryRepository shopCategoryRepository,
                                              ShopProductRepository shopProductRepository) {
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopProductRepository = shopProductRepository;
    }

    public List<AdminShopCategoryDto> getCategories() {
        CategoryContext context = buildContext();
        List<AdminShopCategoryDto> result = new ArrayList<>();
        appendFlatCategories(null, 0, context, result);
        return result;
    }

    public List<AdminShopCategoryDto> getCategoryTree() {
        return buildCategoryTree(null, 0, buildContext());
    }

    public AdminShopCategoryDto getCategory(UUID categoryId) {
        CategoryContext context = buildContext();
        ShopCategory category = context.categoriesById().get(categoryId);
        if (category == null) {
            throw new ResponseStatusException(NOT_FOUND, "Shop category not found");
        }
        return toDto(category, resolveDepth(category), context, true);
    }

    @Transactional
    public AdminShopCategoryDto createCategory(AdminUpsertShopCategoryRequest payload) {
        ensurePayload(payload);
        LocalizedCategoryContent localizedContent = normalizeLocalizedCategoryContent(payload);
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), localizedContent.defaultName());
        ensureSlugAvailable(normalizedSlug, null);

        ShopCategory category = new ShopCategory();
        category.setCreatedAt(OffsetDateTime.now());
        applyPayload(category, payload, localizedContent, normalizedSlug, null);

        ShopCategory saved = shopCategoryRepository.save(category);
        return getCategory(saved.getId());
    }

    @Transactional
    public AdminShopCategoryDto updateCategory(UUID categoryId, AdminUpsertShopCategoryRequest payload) {
        ensurePayload(payload);

        ShopCategory category = shopCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shop category not found"));

        LocalizedCategoryContent localizedContent = normalizeLocalizedCategoryContent(payload);
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), localizedContent.defaultName());
        ensureSlugAvailable(normalizedSlug, category.getId());

        applyPayload(category, payload, localizedContent, normalizedSlug, category.getId());
        ShopCategory saved = shopCategoryRepository.save(category);
        return getCategory(saved.getId());
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        ShopCategory category = shopCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shop category not found"));

        if (shopCategoryRepository.existsByParentCategory_Id(categoryId)) {
            throw new ResponseStatusException(CONFLICT, "Category has child categories and cannot be deleted");
        }
        if (shopProductRepository.existsByCategory_Id(categoryId)) {
            throw new ResponseStatusException(CONFLICT, "Category has products and cannot be deleted");
        }

        shopCategoryRepository.delete(category);
    }

    private void applyPayload(ShopCategory category,
                              AdminUpsertShopCategoryRequest payload,
                              LocalizedCategoryContent localizedContent,
                              String normalizedSlug,
                              UUID currentCategoryId) {
        ShopCategory parentCategory = resolveParentCategory(payload.getParentCategoryId(), currentCategoryId);

        category.setParentCategory(parentCategory);
        category.setSlug(normalizedSlug);
        category.setName(localizedContent.defaultName());
        category.setNameIt(localizedContent.names().get("it"));
        category.setNameEn(localizedContent.names().get("en"));
        category.setNameDe(localizedContent.names().get("de"));
        category.setNameFr(localizedContent.names().get("fr"));
        category.setDescription(localizedContent.defaultDescription());
        category.setDescriptionIt(localizedContent.descriptions().get("it"));
        category.setDescriptionEn(localizedContent.descriptions().get("en"));
        category.setDescriptionDe(localizedContent.descriptions().get("de"));
        category.setDescriptionFr(localizedContent.descriptions().get("fr"));
        category.setSeoTitle(localizedContent.defaultSeoTitle());
        category.setSeoTitleIt(localizedContent.seoTitles().get("it"));
        category.setSeoTitleEn(localizedContent.seoTitles().get("en"));
        category.setSeoTitleDe(localizedContent.seoTitles().get("de"));
        category.setSeoTitleFr(localizedContent.seoTitles().get("fr"));
        category.setSeoDescription(localizedContent.defaultSeoDescription());
        category.setSeoDescriptionIt(localizedContent.seoDescriptions().get("it"));
        category.setSeoDescriptionEn(localizedContent.seoDescriptions().get("en"));
        category.setSeoDescriptionDe(localizedContent.seoDescriptions().get("de"));
        category.setSeoDescriptionFr(localizedContent.seoDescriptions().get("fr"));
        category.setOgTitle(normalizeOptional(payload.getOgTitle()));
        category.setOgDescription(normalizeOptional(payload.getOgDescription()));
        category.setIndexable(payload.getIndexable() == null || payload.getIndexable());
        category.setIsActive(payload.getIsActive() == null || payload.getIsActive());
        category.setSortOrder(payload.getSortOrder() != null ? payload.getSortOrder() : 0);
        category.setUpdatedAt(OffsetDateTime.now());
    }

    private ShopCategory resolveParentCategory(UUID parentCategoryId, UUID currentCategoryId) {
        if (parentCategoryId == null) {
            return null;
        }
        if (currentCategoryId != null && currentCategoryId.equals(parentCategoryId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Category cannot be its own parent");
        }

        ShopCategory parentCategory = shopCategoryRepository.findById(parentCategoryId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Parent category not found"));

        if (currentCategoryId != null) {
            ShopCategory ancestor = parentCategory;
            while (ancestor != null) {
                if (currentCategoryId.equals(ancestor.getId())) {
                    throw new ResponseStatusException(BAD_REQUEST, "Category hierarchy would create a cycle");
                }
                ancestor = ancestor.getParentCategory();
            }
        }

        return parentCategory;
    }

    private void ensurePayload(AdminUpsertShopCategoryRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Payload is required");
        }
    }

    private String normalizeAndValidateSlug(String slug, String fallbackName) {
        String source = normalizeOptional(slug);
        if (source == null) {
            source = fallbackName;
        }

        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC_PATTERN.matcher(normalized).replaceAll("-");
        normalized = EDGE_DASH_PATTERN.matcher(normalized).replaceAll("");

        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Slug is invalid");
        }
        return normalized;
    }

    private void ensureSlugAvailable(String slug, UUID currentCategoryId) {
        shopCategoryRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (currentCategoryId == null || !existing.getId().equals(currentCategoryId)) {
                throw new ResponseStatusException(BAD_REQUEST, "Category slug already exists");
            }
        });
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return normalized;
    }

    private LocalizedCategoryContent normalizeLocalizedCategoryContent(AdminUpsertShopCategoryRequest payload) {
        String legacyName = normalizeOptional(payload.getName());
        String fallbackName = firstNonBlank(
                legacyName,
                normalizeOptional(payload.getNameIt()),
                normalizeOptional(payload.getNameEn()),
                normalizeOptional(payload.getNameDe()),
                normalizeOptional(payload.getNameFr())
        );
        if (fallbackName == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Category name is required");
        }

        Map<String, String> names = new LinkedHashMap<>();
        names.put("it", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameIt()), fallbackName), "Italian category name is required"));
        names.put("en", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameEn()), fallbackName), "English category name is required"));
        names.put("de", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameDe()), fallbackName), "German category name is required"));
        names.put("fr", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameFr()), fallbackName), "French category name is required"));

        String fallbackDescription = firstNonBlank(
                normalizeOptional(payload.getDescription()),
                normalizeOptional(payload.getDescriptionIt()),
                normalizeOptional(payload.getDescriptionEn()),
                normalizeOptional(payload.getDescriptionDe()),
                normalizeOptional(payload.getDescriptionFr())
        );
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("it", firstNonBlank(normalizeOptional(payload.getDescriptionIt()), fallbackDescription));
        descriptions.put("en", firstNonBlank(normalizeOptional(payload.getDescriptionEn()), fallbackDescription));
        descriptions.put("de", firstNonBlank(normalizeOptional(payload.getDescriptionDe()), fallbackDescription));
        descriptions.put("fr", firstNonBlank(normalizeOptional(payload.getDescriptionFr()), fallbackDescription));

        String fallbackSeoTitle = firstNonBlank(
                normalizeOptional(payload.getSeoTitle()),
                normalizeOptional(payload.getSeoTitleIt()),
                normalizeOptional(payload.getSeoTitleEn()),
                normalizeOptional(payload.getSeoTitleDe()),
                normalizeOptional(payload.getSeoTitleFr())
        );
        Map<String, String> seoTitles = new LinkedHashMap<>();
        seoTitles.put("it", firstNonBlank(normalizeOptional(payload.getSeoTitleIt()), fallbackSeoTitle));
        seoTitles.put("en", firstNonBlank(normalizeOptional(payload.getSeoTitleEn()), fallbackSeoTitle));
        seoTitles.put("de", firstNonBlank(normalizeOptional(payload.getSeoTitleDe()), fallbackSeoTitle));
        seoTitles.put("fr", firstNonBlank(normalizeOptional(payload.getSeoTitleFr()), fallbackSeoTitle));

        String fallbackSeoDescription = firstNonBlank(
                normalizeOptional(payload.getSeoDescription()),
                normalizeOptional(payload.getSeoDescriptionIt()),
                normalizeOptional(payload.getSeoDescriptionEn()),
                normalizeOptional(payload.getSeoDescriptionDe()),
                normalizeOptional(payload.getSeoDescriptionFr())
        );
        Map<String, String> seoDescriptions = new LinkedHashMap<>();
        seoDescriptions.put("it", validateSeoDescriptionLength(firstNonBlank(normalizeOptional(payload.getSeoDescriptionIt()), fallbackSeoDescription), "Italian"));
        seoDescriptions.put("en", validateSeoDescriptionLength(firstNonBlank(normalizeOptional(payload.getSeoDescriptionEn()), fallbackSeoDescription), "English"));
        seoDescriptions.put("de", validateSeoDescriptionLength(firstNonBlank(normalizeOptional(payload.getSeoDescriptionDe()), fallbackSeoDescription), "German"));
        seoDescriptions.put("fr", validateSeoDescriptionLength(firstNonBlank(normalizeOptional(payload.getSeoDescriptionFr()), fallbackSeoDescription), "French"));

        return new LocalizedCategoryContent(
                names.get("it"),
                firstNonBlank(descriptions.get("it"), fallbackDescription),
                firstNonBlank(seoTitles.get("it"), fallbackSeoTitle),
                firstNonBlank(seoDescriptions.get("it"), fallbackSeoDescription),
                names,
                descriptions,
                seoTitles,
                seoDescriptions
        );
    }

    private String validateSeoDescriptionLength(String value, String languageLabel) {
        if (value != null && value.length() > 160) {
            throw new ResponseStatusException(BAD_REQUEST, languageLabel + " SEO description must be at most 160 characters");
        }
        return value;
    }

    private String firstNonBlank(String... values) {
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

    private CategoryContext buildContext() {
        List<ShopCategory> categories = shopCategoryRepository.findAllByOrderBySortOrderAscNameAsc();
        List<ShopProduct> products = shopProductRepository.findAll();

        Map<UUID, ShopCategory> categoriesById = categories.stream()
                .collect(Collectors.toMap(ShopCategory::getId, category -> category, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, List<ShopCategory>> childrenByParentId = new LinkedHashMap<>();
        for (ShopCategory category : categories) {
            UUID parentId = category.getParentCategory() != null ? category.getParentCategory().getId() : null;
            childrenByParentId.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(category);
        }
        Comparator<ShopCategory> comparator = Comparator
                .comparing(ShopCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ShopCategory::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        childrenByParentId.values().forEach(children -> children.sort(comparator));

        Map<UUID, Integer> directProductCounts = new LinkedHashMap<>();
        for (ShopProduct product : products) {
            if (product.getCategory() == null || product.getCategory().getId() == null) {
                continue;
            }
            directProductCounts.merge(product.getCategory().getId(), 1, Integer::sum);
        }

        Map<UUID, Integer> descendantProductCounts = new LinkedHashMap<>();
        for (ShopCategory category : categories) {
            resolveDescendantProductCount(category.getId(), childrenByParentId, directProductCounts, descendantProductCounts);
        }

        return new CategoryContext(categoriesById, childrenByParentId, directProductCounts, descendantProductCounts);
    }

    private int resolveDescendantProductCount(UUID categoryId,
                                              Map<UUID, List<ShopCategory>> childrenByParentId,
                                              Map<UUID, Integer> directProductCounts,
                                              Map<UUID, Integer> descendantProductCounts) {
        Integer cached = descendantProductCounts.get(categoryId);
        if (cached != null) {
            return cached;
        }

        int total = directProductCounts.getOrDefault(categoryId, 0);
        for (ShopCategory child : childrenByParentId.getOrDefault(categoryId, List.of())) {
            total += resolveDescendantProductCount(child.getId(), childrenByParentId, directProductCounts, descendantProductCounts);
        }
        descendantProductCounts.put(categoryId, total);
        return total;
    }

    private void appendFlatCategories(UUID parentId,
                                      int depth,
                                      CategoryContext context,
                                      List<AdminShopCategoryDto> result) {
        for (ShopCategory category : context.childrenByParentId().getOrDefault(parentId, List.of())) {
            result.add(toDto(category, depth, context, false));
            appendFlatCategories(category.getId(), depth + 1, context, result);
        }
    }

    private List<AdminShopCategoryDto> buildCategoryTree(UUID parentId, int depth, CategoryContext context) {
        return context.childrenByParentId().getOrDefault(parentId, List.of()).stream()
                .map(category -> toDto(category, depth, context, true))
                .toList();
    }

    private AdminShopCategoryDto toDto(ShopCategory category,
                                       int depth,
                                       CategoryContext context,
                                       boolean includeChildren) {
        AdminShopCategoryDto dto = new AdminShopCategoryDto();
        dto.setId(category.getId());
        dto.setParentCategoryId(category.getParentCategory() != null ? category.getParentCategory().getId() : null);
        dto.setParentCategoryName(category.getParentCategory() != null ? category.getParentCategory().getName() : null);
        dto.setSlug(category.getSlug());
        dto.setName(category.getName());
        dto.setNameIt(category.getNameIt());
        dto.setNameEn(category.getNameEn());
        dto.setNameDe(category.getNameDe());
        dto.setNameFr(category.getNameFr());
        dto.setDescription(category.getDescription());
        dto.setDescriptionIt(category.getDescriptionIt());
        dto.setDescriptionEn(category.getDescriptionEn());
        dto.setDescriptionDe(category.getDescriptionDe());
        dto.setDescriptionFr(category.getDescriptionFr());
        dto.setSeoTitle(category.getSeoTitle());
        dto.setSeoTitleIt(category.getSeoTitleIt());
        dto.setSeoTitleEn(category.getSeoTitleEn());
        dto.setSeoTitleDe(category.getSeoTitleDe());
        dto.setSeoTitleFr(category.getSeoTitleFr());
        dto.setSeoDescription(category.getSeoDescription());
        dto.setSeoDescriptionIt(category.getSeoDescriptionIt());
        dto.setSeoDescriptionEn(category.getSeoDescriptionEn());
        dto.setSeoDescriptionDe(category.getSeoDescriptionDe());
        dto.setSeoDescriptionFr(category.getSeoDescriptionFr());
        dto.setOgTitle(category.getOgTitle());
        dto.setOgDescription(category.getOgDescription());
        dto.setIndexable(category.getIndexable());
        dto.setIsActive(category.getIsActive());
        dto.setSortOrder(category.getSortOrder());
        dto.setDepth(depth);
        dto.setChildCount(context.childrenByParentId().getOrDefault(category.getId(), List.of()).size());
        dto.setDirectProductCount(context.directProductCounts().getOrDefault(category.getId(), 0));
        dto.setDescendantProductCount(context.descendantProductCounts().getOrDefault(category.getId(), 0));
        dto.setMediaUsageType(SHOP_CATEGORY_MEDIA_USAGE_TYPE);
        dto.setMediaUsageKey(category.getId().toString());
        dto.setBreadcrumbs(buildBreadcrumbs(category));
        dto.setChildren(includeChildren ? buildCategoryTree(category.getId(), depth + 1, context) : List.of());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        return dto;
    }

    private List<AdminShopCategoryRefDto> buildBreadcrumbs(ShopCategory category) {
        List<AdminShopCategoryRefDto> breadcrumbs = new ArrayList<>();
        ShopCategory current = category;
        while (current != null) {
            AdminShopCategoryRefDto ref = new AdminShopCategoryRefDto();
            ref.setId(current.getId());
            ref.setSlug(current.getSlug());
            ref.setName(current.getName());
            breadcrumbs.add(ref);
            current = current.getParentCategory();
        }
        java.util.Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private int resolveDepth(ShopCategory category) {
        int depth = 0;
        ShopCategory current = category != null ? category.getParentCategory() : null;
        while (current != null) {
            depth++;
            current = current.getParentCategory();
        }
        return depth;
    }

    private record CategoryContext(
            Map<UUID, ShopCategory> categoriesById,
            Map<UUID, List<ShopCategory>> childrenByParentId,
            Map<UUID, Integer> directProductCounts,
            Map<UUID, Integer> descendantProductCounts
    ) {
    }

    private record LocalizedCategoryContent(
            String defaultName,
            String defaultDescription,
            String defaultSeoTitle,
            String defaultSeoDescription,
            Map<String, String> names,
            Map<String, String> descriptions,
            Map<String, String> seoTitles,
            Map<String, String> seoDescriptions
    ) {
    }
}

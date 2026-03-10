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
        String normalizedName = normalizeRequiredName(payload.getName());
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), normalizedName);
        ensureSlugAvailable(normalizedSlug, null);

        ShopCategory category = new ShopCategory();
        category.setCreatedAt(OffsetDateTime.now());
        applyPayload(category, payload, normalizedName, normalizedSlug, null);

        ShopCategory saved = shopCategoryRepository.save(category);
        return getCategory(saved.getId());
    }

    @Transactional
    public AdminShopCategoryDto updateCategory(UUID categoryId, AdminUpsertShopCategoryRequest payload) {
        ensurePayload(payload);

        ShopCategory category = shopCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shop category not found"));

        String normalizedName = normalizeRequiredName(payload.getName());
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), normalizedName);
        ensureSlugAvailable(normalizedSlug, category.getId());

        applyPayload(category, payload, normalizedName, normalizedSlug, category.getId());
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
                              String normalizedName,
                              String normalizedSlug,
                              UUID currentCategoryId) {
        ShopCategory parentCategory = resolveParentCategory(payload.getParentCategoryId(), currentCategoryId);

        category.setParentCategory(parentCategory);
        category.setSlug(normalizedSlug);
        category.setName(normalizedName);
        category.setDescription(normalizeOptional(payload.getDescription()));
        category.setSeoTitle(normalizeOptional(payload.getSeoTitle()));
        category.setSeoDescription(normalizeOptional(payload.getSeoDescription()));
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

    private String normalizeRequiredName(String name) {
        String normalized = normalizeOptional(name);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Category name is required");
        }
        return normalized;
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
        dto.setDescription(category.getDescription());
        dto.setSeoTitle(category.getSeoTitle());
        dto.setSeoDescription(category.getSeoDescription());
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
}

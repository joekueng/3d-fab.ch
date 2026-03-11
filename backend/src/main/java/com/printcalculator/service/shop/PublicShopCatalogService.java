package com.printcalculator.service.shop;

import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.dto.ShopCategoryDetailDto;
import com.printcalculator.dto.ShopCategoryRefDto;
import com.printcalculator.dto.ShopCategoryTreeDto;
import com.printcalculator.dto.ShopProductCatalogResponseDto;
import com.printcalculator.dto.ShopProductDetailDto;
import com.printcalculator.dto.ShopProductModelDto;
import com.printcalculator.dto.ShopProductSummaryDto;
import com.printcalculator.dto.ShopProductVariantOptionDto;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductModelAsset;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductModelAssetRepository;
import com.printcalculator.repository.ShopProductRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PublicShopCatalogService {
    private static final String SHOP_CATEGORY_MEDIA_USAGE_TYPE = "SHOP_CATEGORY";
    private static final String SHOP_PRODUCT_MEDIA_USAGE_TYPE = "SHOP_PRODUCT";

    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopProductRepository shopProductRepository;
    private final ShopProductVariantRepository shopProductVariantRepository;
    private final ShopProductModelAssetRepository shopProductModelAssetRepository;
    private final FilamentVariantRepository filamentVariantRepository;
    private final PublicMediaQueryService publicMediaQueryService;
    private final ShopStorageService shopStorageService;

    public PublicShopCatalogService(ShopCategoryRepository shopCategoryRepository,
                                    ShopProductRepository shopProductRepository,
                                    ShopProductVariantRepository shopProductVariantRepository,
                                    ShopProductModelAssetRepository shopProductModelAssetRepository,
                                    FilamentVariantRepository filamentVariantRepository,
                                    PublicMediaQueryService publicMediaQueryService,
                                    ShopStorageService shopStorageService) {
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopProductRepository = shopProductRepository;
        this.shopProductVariantRepository = shopProductVariantRepository;
        this.shopProductModelAssetRepository = shopProductModelAssetRepository;
        this.filamentVariantRepository = filamentVariantRepository;
        this.publicMediaQueryService = publicMediaQueryService;
        this.shopStorageService = shopStorageService;
    }

    public List<ShopCategoryTreeDto> getCategories(String language) {
        CategoryContext categoryContext = loadCategoryContext(language);
        return buildCategoryTree(null, categoryContext);
    }

    public ShopCategoryDetailDto getCategory(String slug, String language) {
        ShopCategory category = shopCategoryRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        CategoryContext categoryContext = loadCategoryContext(language);
        if (!categoryContext.categoriesById().containsKey(category.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }

        return buildCategoryDetail(category, categoryContext);
    }

    public ShopProductCatalogResponseDto getProductCatalog(String categorySlug, Boolean featuredOnly, String language) {
        CategoryContext categoryContext = loadCategoryContext(language);
        PublicProductContext productContext = loadPublicProductContext(categoryContext, language);

        ShopCategory selectedCategory = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            selectedCategory = categoryContext.categoriesBySlug().get(categorySlug.trim());
            if (selectedCategory == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
            }
        }

        Collection<UUID> allowedCategoryIds = selectedCategory == null
                ? categoryContext.categoriesById().keySet()
                : resolveDescendantCategoryIds(selectedCategory.getId(), categoryContext.childrenByParentId());

        List<ShopProductSummaryDto> products = productContext.entries().stream()
                .filter(entry -> allowedCategoryIds.contains(entry.product().getCategory().getId()))
                .filter(entry -> !Boolean.TRUE.equals(featuredOnly) || Boolean.TRUE.equals(entry.product().getIsFeatured()))
                .map(entry -> toProductSummaryDto(
                        entry,
                        productContext.productMediaBySlug(),
                        productContext.variantColorHexByMaterialAndColor(),
                        language
                ))
                .toList();

        ShopCategoryDetailDto selectedCategoryDetail = selectedCategory != null
                ? buildCategoryDetail(selectedCategory, categoryContext)
                : null;

        return new ShopProductCatalogResponseDto(
                selectedCategory != null ? selectedCategory.getSlug() : null,
                Boolean.TRUE.equals(featuredOnly),
                selectedCategoryDetail,
                products
        );
    }

    public ShopProductDetailDto getProduct(String slug, String language) {
        CategoryContext categoryContext = loadCategoryContext(language);
        PublicProductContext productContext = loadPublicProductContext(categoryContext, language);

        ProductEntry entry = productContext.entriesBySlug().get(slug);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        ShopCategory category = entry.product().getCategory();
        if (category == null || !categoryContext.categoriesById().containsKey(category.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        return toProductDetailDto(
                entry,
                productContext.productMediaBySlug(),
                productContext.variantColorHexByMaterialAndColor(),
                language
        );
    }

    public ProductModelDownload getProductModelDownload(String slug) {
        CategoryContext categoryContext = loadCategoryContext(null);
        PublicProductContext productContext = loadPublicProductContext(categoryContext, null);
        ProductEntry entry = productContext.entriesBySlug().get(slug);
        if (entry == null || entry.modelAsset() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product model not found");
        }

        Path path = shopStorageService.resolveStoredProductPath(
                entry.modelAsset().getStoredRelativePath(),
                entry.product().getId()
        );
        if (path == null || !Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product model not found");
        }

        return new ProductModelDownload(
                path,
                entry.modelAsset().getOriginalFilename(),
                entry.modelAsset().getMimeType()
        );
    }

    private CategoryContext loadCategoryContext(String language) {
        List<ShopCategory> categories = shopCategoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc();

        Map<UUID, ShopCategory> categoriesById = categories.stream()
                .collect(Collectors.toMap(ShopCategory::getId, category -> category, (left, right) -> left, LinkedHashMap::new));
        Map<String, ShopCategory> categoriesBySlug = categories.stream()
                .collect(Collectors.toMap(ShopCategory::getSlug, category -> category, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, List<ShopCategory>> childrenByParentId = buildChildrenByParentId(categories);

        List<ProductEntry> publicProducts = loadPublicProducts(categoriesById.keySet());
        Map<UUID, Integer> descendantProductCounts = resolveDescendantProductCounts(categories, childrenByParentId, publicProducts);
        Map<String, List<PublicMediaUsageDto>> categoryMediaBySlug = publicMediaQueryService.getUsageMediaMap(
                SHOP_CATEGORY_MEDIA_USAGE_TYPE,
                categories.stream().map(this::categoryMediaUsageKey).toList(),
                language
        );

        return new CategoryContext(
                categoriesById,
                categoriesBySlug,
                childrenByParentId,
                descendantProductCounts,
                categoryMediaBySlug
        );
    }

    private PublicProductContext loadPublicProductContext(CategoryContext categoryContext, String language) {
        List<ProductEntry> entries = loadPublicProducts(categoryContext.categoriesById().keySet());
        Map<String, List<PublicMediaUsageDto>> productMediaBySlug = publicMediaQueryService.getUsageMediaMap(
                SHOP_PRODUCT_MEDIA_USAGE_TYPE,
                entries.stream().map(entry -> productMediaUsageKey(entry.product())).toList(),
                language
        );
        Map<String, String> variantColorHexByMaterialAndColor = buildFilamentVariantColorHexMap();

        Map<String, ProductEntry> entriesBySlug = entries.stream()
                .collect(Collectors.toMap(entry -> entry.product().getSlug(), entry -> entry, (left, right) -> left, LinkedHashMap::new));

        return new PublicProductContext(entries, entriesBySlug, productMediaBySlug, variantColorHexByMaterialAndColor);
    }

    private Map<String, String> buildFilamentVariantColorHexMap() {
        Map<String, String> colorsByMaterialAndColor = new LinkedHashMap<>();
        for (FilamentVariant variant : filamentVariantRepository.findByIsActiveTrue()) {
            String materialCode = variant.getFilamentMaterialType() != null
                    ? variant.getFilamentMaterialType().getMaterialCode()
                    : null;
            String key = toMaterialAndColorKey(materialCode, variant.getColorName());
            if (key == null) {
                continue;
            }

            String colorHex = trimToNull(variant.getColorHex());
            if (colorHex == null) {
                continue;
            }
            colorsByMaterialAndColor.putIfAbsent(key, colorHex);
        }
        return colorsByMaterialAndColor;
    }

    private List<ProductEntry> loadPublicProducts(Collection<UUID> activeCategoryIds) {
        List<ShopProduct> products = shopProductRepository.findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc();
        if (products.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = products.stream().map(ShopProduct::getId).toList();
        Map<UUID, List<ShopProductVariant>> variantsByProductId = shopProductVariantRepository
                .findByProduct_IdInAndIsActiveTrueOrderBySortOrderAscColorNameAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        variant -> variant.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<UUID, ShopProductModelAsset> modelAssetByProductId = shopProductModelAssetRepository.findByProduct_IdIn(productIds)
                .stream()
                .collect(Collectors.toMap(asset -> asset.getProduct().getId(), asset -> asset, (left, right) -> left, LinkedHashMap::new));

        return products.stream()
                .filter(product -> product.getCategory() != null)
                .filter(product -> activeCategoryIds.contains(product.getCategory().getId()))
                .map(product -> {
                    List<ShopProductVariant> activeVariants = variantsByProductId.getOrDefault(product.getId(), List.of());
                    if (activeVariants.isEmpty()) {
                        return null;
                    }
                    ShopProductVariant defaultVariant = pickDefaultVariant(activeVariants);
                    return new ProductEntry(
                            product,
                            activeVariants,
                            defaultVariant,
                            modelAssetByProductId.get(product.getId())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<UUID, List<ShopCategory>> buildChildrenByParentId(List<ShopCategory> categories) {
        Map<UUID, List<ShopCategory>> childrenByParentId = new LinkedHashMap<>();
        for (ShopCategory category : categories) {
            UUID parentId = category.getParentCategory() != null ? category.getParentCategory().getId() : null;
            childrenByParentId.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(category);
        }
        Comparator<ShopCategory> comparator = Comparator
                .comparing(ShopCategory::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ShopCategory::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        childrenByParentId.values().forEach(children -> children.sort(comparator));
        return childrenByParentId;
    }

    private Map<UUID, Integer> resolveDescendantProductCounts(List<ShopCategory> categories,
                                                              Map<UUID, List<ShopCategory>> childrenByParentId,
                                                              List<ProductEntry> publicProducts) {
        Map<UUID, Integer> directProductCounts = new LinkedHashMap<>();
        for (ProductEntry entry : publicProducts) {
            UUID categoryId = entry.product().getCategory().getId();
            directProductCounts.merge(categoryId, 1, Integer::sum);
        }

        Map<UUID, Integer> descendantCounts = new LinkedHashMap<>();
        for (ShopCategory category : categories) {
            resolveCategoryProductCount(category.getId(), childrenByParentId, directProductCounts, descendantCounts);
        }
        return descendantCounts;
    }

    private int resolveCategoryProductCount(UUID categoryId,
                                            Map<UUID, List<ShopCategory>> childrenByParentId,
                                            Map<UUID, Integer> directProductCounts,
                                            Map<UUID, Integer> descendantCounts) {
        Integer cached = descendantCounts.get(categoryId);
        if (cached != null) {
            return cached;
        }

        int total = directProductCounts.getOrDefault(categoryId, 0);
        for (ShopCategory child : childrenByParentId.getOrDefault(categoryId, List.of())) {
            total += resolveCategoryProductCount(child.getId(), childrenByParentId, directProductCounts, descendantCounts);
        }
        descendantCounts.put(categoryId, total);
        return total;
    }

    private List<ShopCategoryTreeDto> buildCategoryTree(UUID parentId, CategoryContext categoryContext) {
        return categoryContext.childrenByParentId().getOrDefault(parentId, List.of()).stream()
                .map(category -> new ShopCategoryTreeDto(
                        category.getId(),
                        category.getParentCategory() != null ? category.getParentCategory().getId() : null,
                        category.getSlug(),
                        category.getName(),
                        category.getDescription(),
                        category.getSeoTitle(),
                        category.getSeoDescription(),
                        category.getOgTitle(),
                        category.getOgDescription(),
                        category.getIndexable(),
                        category.getSortOrder(),
                        categoryContext.descendantProductCounts().getOrDefault(category.getId(), 0),
                        selectPrimaryMedia(categoryContext.categoryMediaBySlug().get(categoryMediaUsageKey(category))),
                        buildCategoryTree(category.getId(), categoryContext)
                ))
                .toList();
    }

    private ShopCategoryDetailDto buildCategoryDetail(ShopCategory category, CategoryContext categoryContext) {
        List<PublicMediaUsageDto> images = categoryContext.categoryMediaBySlug().getOrDefault(categoryMediaUsageKey(category), List.of());
        return new ShopCategoryDetailDto(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getDescription(),
                category.getSeoTitle(),
                category.getSeoDescription(),
                category.getOgTitle(),
                category.getOgDescription(),
                category.getIndexable(),
                category.getSortOrder(),
                categoryContext.descendantProductCounts().getOrDefault(category.getId(), 0),
                buildCategoryBreadcrumbs(category),
                selectPrimaryMedia(images),
                images,
                buildCategoryTree(category.getId(), categoryContext)
        );
    }

    private List<ShopCategoryRefDto> buildCategoryBreadcrumbs(ShopCategory category) {
        List<ShopCategoryRefDto> breadcrumbs = new ArrayList<>();
        ShopCategory current = category;
        while (current != null) {
            breadcrumbs.add(new ShopCategoryRefDto(current.getId(), current.getSlug(), current.getName()));
            current = current.getParentCategory();
        }
        java.util.Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private List<UUID> resolveDescendantCategoryIds(UUID rootId, Map<UUID, List<ShopCategory>> childrenByParentId) {
        List<UUID> ids = new ArrayList<>();
        collectDescendantCategoryIds(rootId, childrenByParentId, ids);
        return ids;
    }

    private void collectDescendantCategoryIds(UUID categoryId,
                                              Map<UUID, List<ShopCategory>> childrenByParentId,
                                              List<UUID> accumulator) {
        accumulator.add(categoryId);
        for (ShopCategory child : childrenByParentId.getOrDefault(categoryId, List.of())) {
            collectDescendantCategoryIds(child.getId(), childrenByParentId, accumulator);
        }
    }

    private ShopProductSummaryDto toProductSummaryDto(ProductEntry entry,
                                                      Map<String, List<PublicMediaUsageDto>> productMediaBySlug,
                                                      Map<String, String> variantColorHexByMaterialAndColor,
                                                      String language) {
        List<PublicMediaUsageDto> images = productMediaBySlug.getOrDefault(productMediaUsageKey(entry.product()), List.of());
        return new ShopProductSummaryDto(
                entry.product().getId(),
                entry.product().getSlug(),
                entry.product().getNameForLanguage(language),
                entry.product().getExcerptForLanguage(language),
                entry.product().getIsFeatured(),
                entry.product().getSortOrder(),
                new ShopCategoryRefDto(
                        entry.product().getCategory().getId(),
                        entry.product().getCategory().getSlug(),
                        entry.product().getCategory().getName()
                ),
                resolvePriceFrom(entry.variants()),
                resolvePriceTo(entry.variants()),
                toVariantDto(entry.defaultVariant(), entry.defaultVariant(), variantColorHexByMaterialAndColor),
                selectPrimaryMedia(images),
                toProductModelDto(entry)
        );
    }

    private ShopProductDetailDto toProductDetailDto(ProductEntry entry,
                                                    Map<String, List<PublicMediaUsageDto>> productMediaBySlug,
                                                    Map<String, String> variantColorHexByMaterialAndColor,
                                                    String language) {
        List<PublicMediaUsageDto> images = productMediaBySlug.getOrDefault(productMediaUsageKey(entry.product()), List.of());
        String localizedSeoTitle = entry.product().getSeoTitleForLanguage(language);
        String localizedSeoDescription = entry.product().getSeoDescriptionForLanguage(language);
        return new ShopProductDetailDto(
                entry.product().getId(),
                entry.product().getSlug(),
                entry.product().getNameForLanguage(language),
                entry.product().getExcerptForLanguage(language),
                entry.product().getDescriptionForLanguage(language),
                localizedSeoTitle,
                localizedSeoDescription,
                localizedSeoTitle,
                localizedSeoDescription,
                entry.product().getIndexable(),
                entry.product().getIsFeatured(),
                entry.product().getSortOrder(),
                new ShopCategoryRefDto(
                        entry.product().getCategory().getId(),
                        entry.product().getCategory().getSlug(),
                        entry.product().getCategory().getName()
                ),
                buildCategoryBreadcrumbs(entry.product().getCategory()),
                resolvePriceFrom(entry.variants()),
                resolvePriceTo(entry.variants()),
                toVariantDto(entry.defaultVariant(), entry.defaultVariant(), variantColorHexByMaterialAndColor),
                entry.variants().stream()
                        .map(variant -> toVariantDto(variant, entry.defaultVariant(), variantColorHexByMaterialAndColor))
                        .toList(),
                selectPrimaryMedia(images),
                images,
                toProductModelDto(entry)
        );
    }

    private ShopProductVariantOptionDto toVariantDto(ShopProductVariant variant,
                                                     ShopProductVariant defaultVariant,
                                                     Map<String, String> variantColorHexByMaterialAndColor) {
        if (variant == null) {
            return null;
        }
        String colorHex = trimToNull(variant.getColorHex());
        if (colorHex == null) {
            String key = toMaterialAndColorKey(variant.getInternalMaterialCode(), variant.getColorName());
            colorHex = key != null ? variantColorHexByMaterialAndColor.get(key) : null;
        }
        return new ShopProductVariantOptionDto(
                variant.getId(),
                variant.getSku(),
                variant.getVariantLabel(),
                variant.getColorName(),
                colorHex,
                variant.getPriceChf(),
                defaultVariant != null && Objects.equals(defaultVariant.getId(), variant.getId())
        );
    }

    private String toMaterialAndColorKey(String materialCode, String colorName) {
        String normalizedMaterialCode = normalizeMaterialCode(materialCode);
        String normalizedColorName = normalizeColorName(colorName);
        if (normalizedMaterialCode == null || normalizedColorName == null) {
            return null;
        }
        return normalizedMaterialCode + "|" + normalizedColorName;
    }

    private String normalizeMaterialCode(String materialCode) {
        String raw = trimToNull(materialCode);
        if (raw == null) {
            return null;
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private String normalizeColorName(String colorName) {
        String raw = trimToNull(colorName);
        if (raw == null) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        return raw;
    }

    private ShopProductModelDto toProductModelDto(ProductEntry entry) {
        if (entry.modelAsset() == null) {
            return null;
        }
        return new ShopProductModelDto(
                "/api/shop/products/" + entry.product().getSlug() + "/model",
                entry.modelAsset().getOriginalFilename(),
                entry.modelAsset().getMimeType(),
                entry.modelAsset().getFileSizeBytes(),
                entry.modelAsset().getBoundingBoxXMm(),
                entry.modelAsset().getBoundingBoxYMm(),
                entry.modelAsset().getBoundingBoxZMm()
        );
    }

    private ShopProductVariant pickDefaultVariant(List<ShopProductVariant> variants) {
        return variants.stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getIsDefault()))
                .findFirst()
                .orElseGet(() -> variants.isEmpty() ? null : variants.get(0));
    }

    private BigDecimal resolvePriceFrom(List<ShopProductVariant> variants) {
        return variants.stream()
                .map(ShopProductVariant::getPriceChf)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal resolvePriceTo(List<ShopProductVariant> variants) {
        return variants.stream()
                .map(ShopProductVariant::getPriceChf)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private PublicMediaUsageDto selectPrimaryMedia(List<PublicMediaUsageDto> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                .findFirst()
                .orElse(images.get(0));
    }

    private String categoryMediaUsageKey(ShopCategory category) {
        return category.getId().toString();
    }

    private String productMediaUsageKey(ShopProduct product) {
        return product.getId().toString();
    }

    public record ProductModelDownload(Path path, String filename, String mimeType) {
    }

    private record CategoryContext(
            Map<UUID, ShopCategory> categoriesById,
            Map<String, ShopCategory> categoriesBySlug,
            Map<UUID, List<ShopCategory>> childrenByParentId,
            Map<UUID, Integer> descendantProductCounts,
            Map<String, List<PublicMediaUsageDto>> categoryMediaBySlug
    ) {
    }

    private record PublicProductContext(
            List<ProductEntry> entries,
            Map<String, ProductEntry> entriesBySlug,
            Map<String, List<PublicMediaUsageDto>> productMediaBySlug,
            Map<String, String> variantColorHexByMaterialAndColor
    ) {
    }

    private record ProductEntry(
            ShopProduct product,
            List<ShopProductVariant> variants,
            ShopProductVariant defaultVariant,
            ShopProductModelAsset modelAsset
    ) {
    }
}

package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminMediaUsageDto;
import com.printcalculator.dto.AdminShopProductDto;
import com.printcalculator.dto.AdminShopProductVariantDto;
import com.printcalculator.dto.AdminUpsertShopProductRequest;
import com.printcalculator.dto.AdminUpsertShopProductVariantRequest;
import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.dto.ShopProductModelDto;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductModelAsset;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.model.ModelDimensions;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductModelAssetRepository;
import com.printcalculator.repository.ShopProductRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.media.PublicMediaQueryService;
import com.printcalculator.service.shop.ShopStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminShopProductControllerService {
    private static final String SHOP_PRODUCT_MEDIA_USAGE_TYPE = "SHOP_PRODUCT";
    private static final Set<String> SUPPORTED_MODEL_EXTENSIONS = Set.of("stl", "3mf");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH_PATTERN = Pattern.compile("(^-+|-+$)");

    private final ShopProductRepository shopProductRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopProductVariantRepository shopProductVariantRepository;
    private final ShopProductModelAssetRepository shopProductModelAssetRepository;
    private final QuoteLineItemRepository quoteLineItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final PublicMediaQueryService publicMediaQueryService;
    private final AdminMediaControllerService adminMediaControllerService;
    private final ShopStorageService shopStorageService;
    private final SlicerService slicerService;
    private final ClamAVService clamAVService;
    private final long maxModelFileSizeBytes;

    public AdminShopProductControllerService(ShopProductRepository shopProductRepository,
                                             ShopCategoryRepository shopCategoryRepository,
                                             ShopProductVariantRepository shopProductVariantRepository,
                                             ShopProductModelAssetRepository shopProductModelAssetRepository,
                                             QuoteLineItemRepository quoteLineItemRepository,
                                             OrderItemRepository orderItemRepository,
                                             PublicMediaQueryService publicMediaQueryService,
                                             AdminMediaControllerService adminMediaControllerService,
                                             ShopStorageService shopStorageService,
                                             SlicerService slicerService,
                                             ClamAVService clamAVService,
                                             @Value("${shop.model.max-file-size-bytes:104857600}") long maxModelFileSizeBytes) {
        this.shopProductRepository = shopProductRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopProductVariantRepository = shopProductVariantRepository;
        this.shopProductModelAssetRepository = shopProductModelAssetRepository;
        this.quoteLineItemRepository = quoteLineItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.publicMediaQueryService = publicMediaQueryService;
        this.adminMediaControllerService = adminMediaControllerService;
        this.shopStorageService = shopStorageService;
        this.slicerService = slicerService;
        this.clamAVService = clamAVService;
        this.maxModelFileSizeBytes = maxModelFileSizeBytes;
    }

    public List<AdminShopProductDto> getProducts() {
        return toProductDtos(shopProductRepository.findAllByOrderByIsFeaturedDescSortOrderAscNameAsc());
    }

    public AdminShopProductDto getProduct(UUID productId) {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));
        return toProductDtos(List.of(product)).get(0);
    }

    @Transactional
    public AdminShopProductDto createProduct(AdminUpsertShopProductRequest payload) {
        ensurePayload(payload);
        LocalizedProductContent localizedContent = normalizeLocalizedProductContent(payload);
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), localizedContent.defaultName());
        ensureSlugAvailable(normalizedSlug, null);

        ShopProduct product = new ShopProduct();
        product.setCreatedAt(OffsetDateTime.now());
        applyProductPayload(product, payload, localizedContent, normalizedSlug, resolveCategory(payload.getCategoryId()));
        ShopProduct saved = shopProductRepository.save(product);
        syncVariants(saved, payload.getVariants());
        return getProduct(saved.getId());
    }

    @Transactional
    public AdminShopProductDto updateProduct(UUID productId, AdminUpsertShopProductRequest payload) {
        ensurePayload(payload);
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));

        LocalizedProductContent localizedContent = normalizeLocalizedProductContent(payload);
        String normalizedSlug = normalizeAndValidateSlug(payload.getSlug(), localizedContent.defaultName());
        ensureSlugAvailable(normalizedSlug, productId);

        applyProductPayload(product, payload, localizedContent, normalizedSlug, resolveCategory(payload.getCategoryId()));
        ShopProduct saved = shopProductRepository.save(product);
        syncVariants(saved, payload.getVariants());
        return getProduct(saved.getId());
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));

        if (quoteLineItemRepository.existsByShopProduct_Id(productId)
                || orderItemRepository.existsByShopProduct_Id(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is already used in carts or orders and cannot be deleted");
        }

        List<ShopProductVariant> variants = shopProductVariantRepository.findByProduct_IdOrderBySortOrderAscColorNameAsc(productId);
        for (ShopProductVariant variant : variants) {
            if (quoteLineItemRepository.existsByShopProductVariant_Id(variant.getId())
                    || orderItemRepository.existsByShopProductVariant_Id(variant.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more variants are already used in carts or orders and cannot be deleted");
            }
        }

        shopProductModelAssetRepository.findByProduct_Id(productId).ifPresent(asset -> deleteExistingModelFile(asset, productId));
        shopProductRepository.delete(product);
    }

    @Transactional
    public AdminShopProductDto uploadProductModel(UUID productId, MultipartFile file) throws IOException {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));
        validateModelUpload(file);

        Path tempDirectory = Files.createTempDirectory("shop-product-model-");
        Path destination = null;
        try {
            String cleanedFilename = sanitizeOriginalFilename(file.getOriginalFilename());
            String extension = resolveExtension(cleanedFilename);
            Path uploadPath = tempDirectory.resolve("upload." + extension);
            file.transferTo(uploadPath);

            try (InputStream inputStream = Files.newInputStream(uploadPath)) {
                clamAVService.scan(inputStream);
            }

            Path storageDir = shopStorageService.productModelStorageDir(productId);
            destination = storageDir.resolve(UUID.randomUUID() + ".stl");
            if ("3mf".equals(extension)) {
                slicerService.convert3mfToPersistentStl(uploadPath.toFile(), destination);
            } else {
                Files.copy(uploadPath, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            ModelDimensions dimensions = slicerService.inspectModelDimensions(destination.toFile())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to extract model dimensions"));

            ShopProductModelAsset asset = shopProductModelAssetRepository.findByProduct_Id(productId)
                    .orElseGet(ShopProductModelAsset::new);
            String previousStoredRelativePath = asset.getStoredRelativePath();

            asset.setProduct(product);
            asset.setOriginalFilename(buildDownloadFilename(cleanedFilename));
            asset.setStoredFilename(destination.getFileName().toString());
            asset.setStoredRelativePath(shopStorageService.toStoredPath(destination));
            asset.setMimeType("model/stl");
            asset.setFileSizeBytes(Files.size(destination));
            asset.setSha256Hex(computeSha256(destination));
            asset.setBoundingBoxXMm(BigDecimal.valueOf(dimensions.xMm()));
            asset.setBoundingBoxYMm(BigDecimal.valueOf(dimensions.yMm()));
            asset.setBoundingBoxZMm(BigDecimal.valueOf(dimensions.zMm()));
            if (asset.getCreatedAt() == null) {
                asset.setCreatedAt(OffsetDateTime.now());
            }
            asset.setUpdatedAt(OffsetDateTime.now());
            shopProductModelAssetRepository.save(asset);
            deleteStoredRelativePath(previousStoredRelativePath, productId, asset.getStoredRelativePath());

            return getProduct(productId);
        } catch (IOException | RuntimeException e) {
            deletePathQuietly(destination);
            throw e;
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    @Transactional
    public void deleteProductModel(UUID productId) {
        shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));
        ShopProductModelAsset asset = shopProductModelAssetRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product model not found"));

        deleteExistingModelFile(asset, productId);
        shopProductModelAssetRepository.delete(asset);
    }

    public ProductModelDownload getProductModel(UUID productId) {
        ShopProduct product = shopProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product not found"));
        ShopProductModelAsset asset = shopProductModelAssetRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product model not found"));

        Path path = shopStorageService.resolveStoredProductPath(asset.getStoredRelativePath(), product.getId());
        if (path == null || !Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop product model not found");
        }

        return new ProductModelDownload(path, asset.getOriginalFilename(), asset.getMimeType());
    }

    private void syncVariants(ShopProduct product, List<AdminUpsertShopProductVariantRequest> variantPayloads) {
        List<AdminUpsertShopProductVariantRequest> normalizedPayloads = normalizeVariantPayloads(variantPayloads);
        List<ShopProductVariant> existingVariants = shopProductVariantRepository.findByProduct_IdOrderBySortOrderAscColorNameAsc(product.getId());
        Map<UUID, ShopProductVariant> existingById = existingVariants.stream()
                .collect(Collectors.toMap(ShopProductVariant::getId, variant -> variant, (left, right) -> left, LinkedHashMap::new));

        Set<UUID> retainedIds = new LinkedHashSet<>();
        List<ShopProductVariant> variantsToSave = new ArrayList<>();

        for (AdminUpsertShopProductVariantRequest payload : normalizedPayloads) {
            ShopProductVariant variant;
            if (payload.getId() != null) {
                variant = existingById.get(payload.getId());
                if (variant == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant does not belong to the product");
                }
                retainedIds.add(variant.getId());
            } else {
                variant = new ShopProductVariant();
                variant.setCreatedAt(OffsetDateTime.now());
            }

            applyVariantPayload(variant, product, payload);
            variantsToSave.add(variant);
        }

        List<ShopProductVariant> variantsToDelete = existingVariants.stream()
                .filter(variant -> !retainedIds.contains(variant.getId()))
                .toList();
        for (ShopProductVariant variant : variantsToDelete) {
            if (quoteLineItemRepository.existsByShopProductVariant_Id(variant.getId())
                    || orderItemRepository.existsByShopProductVariant_Id(variant.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant is already used in carts or orders and cannot be removed");
            }
        }

        if (!variantsToDelete.isEmpty()) {
            shopProductVariantRepository.deleteAll(variantsToDelete);
        }
        shopProductVariantRepository.saveAll(variantsToSave);
    }

    private void applyProductPayload(ShopProduct product,
                                     AdminUpsertShopProductRequest payload,
                                     LocalizedProductContent localizedContent,
                                     String normalizedSlug,
                                     ShopCategory category) {
        product.setCategory(category);
        product.setSlug(normalizedSlug);
        product.setName(localizedContent.defaultName());
        product.setNameIt(localizedContent.names().get("it"));
        product.setNameEn(localizedContent.names().get("en"));
        product.setNameDe(localizedContent.names().get("de"));
        product.setNameFr(localizedContent.names().get("fr"));
        product.setExcerpt(localizedContent.defaultExcerpt());
        product.setExcerptIt(localizedContent.excerpts().get("it"));
        product.setExcerptEn(localizedContent.excerpts().get("en"));
        product.setExcerptDe(localizedContent.excerpts().get("de"));
        product.setExcerptFr(localizedContent.excerpts().get("fr"));
        product.setDescription(localizedContent.defaultDescription());
        product.setDescriptionIt(localizedContent.descriptions().get("it"));
        product.setDescriptionEn(localizedContent.descriptions().get("en"));
        product.setDescriptionDe(localizedContent.descriptions().get("de"));
        product.setDescriptionFr(localizedContent.descriptions().get("fr"));
        product.setSeoTitle(normalizeOptional(payload.getSeoTitle()));
        product.setSeoDescription(normalizeOptional(payload.getSeoDescription()));
        product.setOgTitle(normalizeOptional(payload.getOgTitle()));
        product.setOgDescription(normalizeOptional(payload.getOgDescription()));
        product.setIndexable(payload.getIndexable() == null || payload.getIndexable());
        product.setIsFeatured(Boolean.TRUE.equals(payload.getIsFeatured()));
        product.setIsActive(payload.getIsActive() == null || payload.getIsActive());
        product.setSortOrder(payload.getSortOrder() != null ? payload.getSortOrder() : 0);
        product.setUpdatedAt(OffsetDateTime.now());
    }

    private void applyVariantPayload(ShopProductVariant variant,
                                     ShopProduct product,
                                     AdminUpsertShopProductVariantRequest payload) {
        String normalizedColorName = normalizeRequired(payload.getColorName(), "Variant colorName is required");
        String normalizedVariantLabel = normalizeOptional(payload.getVariantLabel());
        String normalizedSku = normalizeOptional(payload.getSku());
        String normalizedMaterialCode = normalizeRequired(
                payload.getInternalMaterialCode(),
                "Variant internalMaterialCode is required"
        ).toUpperCase(Locale.ROOT);

        BigDecimal price = payload.getPriceChf();
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant priceChf must be >= 0");
        }
        if (price.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant priceChf must have at most 2 decimal places");
        }

        if (normalizedSku != null) {
            if (variant.getId() == null) {
                if (shopProductVariantRepository.existsBySkuIgnoreCase(normalizedSku)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant SKU already exists");
                }
            } else if (shopProductVariantRepository.existsBySkuIgnoreCaseAndIdNot(normalizedSku, variant.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant SKU already exists");
            }
        }

        variant.setProduct(product);
        variant.setSku(normalizedSku);
        variant.setVariantLabel(normalizedVariantLabel != null ? normalizedVariantLabel : normalizedColorName);
        variant.setColorName(normalizedColorName);
        variant.setColorHex(normalizeColorHex(payload.getColorHex()));
        variant.setInternalMaterialCode(normalizedMaterialCode);
        variant.setPriceChf(price);
        variant.setIsDefault(Boolean.TRUE.equals(payload.getIsDefault()));
        variant.setIsActive(payload.getIsActive() == null || payload.getIsActive());
        variant.setSortOrder(payload.getSortOrder() != null ? payload.getSortOrder() : 0);
        variant.setUpdatedAt(OffsetDateTime.now());
    }

    private List<AdminUpsertShopProductVariantRequest> normalizeVariantPayloads(List<AdminUpsertShopProductVariantRequest> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one variant is required");
        }

        List<AdminUpsertShopProductVariantRequest> normalized = new ArrayList<>(payloads);
        Set<String> colorKeys = new LinkedHashSet<>();
        int defaultCount = 0;
        for (AdminUpsertShopProductVariantRequest payload : normalized) {
            if (payload == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant payload is required");
            }
            String colorName = normalizeRequired(payload.getColorName(), "Variant colorName is required");
            String colorKey = colorName.toLowerCase(Locale.ROOT);
            if (!colorKeys.add(colorKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate variant colorName: " + colorName);
            }
            if (Boolean.TRUE.equals(payload.getIsDefault())) {
                defaultCount++;
            }
        }

        if (defaultCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one variant can be default");
        }
        if (defaultCount == 0) {
            AdminUpsertShopProductVariantRequest fallbackDefault = normalized.stream()
                    .filter(payload -> payload.getIsActive() == null || payload.getIsActive())
                    .findFirst()
                    .orElse(normalized.get(0));
            fallbackDefault.setIsDefault(true);
        }
        return normalized;
    }

    private List<AdminShopProductDto> toProductDtos(List<ShopProduct> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = products.stream().map(ShopProduct::getId).toList();
        Map<UUID, List<ShopProductVariant>> variantsByProductId = shopProductVariantRepository
                .findByProduct_IdInOrderBySortOrderAscColorNameAsc(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        variant -> variant.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<UUID, ShopProductModelAsset> modelAssetsByProductId = shopProductModelAssetRepository.findByProduct_IdIn(productIds)
                .stream()
                .collect(Collectors.toMap(asset -> asset.getProduct().getId(), asset -> asset, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<PublicMediaUsageDto>> publicImagesByUsageKey = publicMediaQueryService.getUsageMediaMap(
                SHOP_PRODUCT_MEDIA_USAGE_TYPE,
                products.stream().map(this::mediaUsageKey).toList(),
                null
        );

        return products.stream()
                .map(product -> {
                    String usageKey = mediaUsageKey(product);
                    return toProductDto(
                            product,
                            variantsByProductId.getOrDefault(product.getId(), List.of()),
                            modelAssetsByProductId.get(product.getId()),
                            publicImagesByUsageKey.getOrDefault(usageKey, List.of()),
                            adminMediaControllerService.getUsages(SHOP_PRODUCT_MEDIA_USAGE_TYPE, usageKey, null)
                    );
                })
                .toList();
    }

    private AdminShopProductDto toProductDto(ShopProduct product,
                                             List<ShopProductVariant> variants,
                                             ShopProductModelAsset modelAsset,
                                             List<PublicMediaUsageDto> images,
                                             List<AdminMediaUsageDto> mediaUsages) {
        AdminShopProductDto dto = new AdminShopProductDto();
        dto.setId(product.getId());
        dto.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        dto.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        dto.setCategorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null);
        dto.setSlug(product.getSlug());
        dto.setName(product.getName());
        dto.setNameIt(product.getNameIt());
        dto.setNameEn(product.getNameEn());
        dto.setNameDe(product.getNameDe());
        dto.setNameFr(product.getNameFr());
        dto.setExcerpt(product.getExcerpt());
        dto.setExcerptIt(product.getExcerptIt());
        dto.setExcerptEn(product.getExcerptEn());
        dto.setExcerptDe(product.getExcerptDe());
        dto.setExcerptFr(product.getExcerptFr());
        dto.setDescription(product.getDescription());
        dto.setDescriptionIt(product.getDescriptionIt());
        dto.setDescriptionEn(product.getDescriptionEn());
        dto.setDescriptionDe(product.getDescriptionDe());
        dto.setDescriptionFr(product.getDescriptionFr());
        dto.setSeoTitle(product.getSeoTitle());
        dto.setSeoDescription(product.getSeoDescription());
        dto.setOgTitle(product.getOgTitle());
        dto.setOgDescription(product.getOgDescription());
        dto.setIndexable(product.getIndexable());
        dto.setIsFeatured(product.getIsFeatured());
        dto.setIsActive(product.getIsActive());
        dto.setSortOrder(product.getSortOrder());
        dto.setVariantCount(variants.size());
        dto.setActiveVariantCount((int) variants.stream().filter(variant -> Boolean.TRUE.equals(variant.getIsActive())).count());
        dto.setPriceFromChf(resolvePriceFrom(variants));
        dto.setPriceToChf(resolvePriceTo(variants));
        dto.setMediaUsageType(SHOP_PRODUCT_MEDIA_USAGE_TYPE);
        dto.setMediaUsageKey(mediaUsageKey(product));
        dto.setMediaUsages(mediaUsages);
        dto.setImages(images);
        dto.setModel3d(toModelDto(product, modelAsset));
        dto.setVariants(variants.stream().map(this::toVariantDto).toList());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }

    private AdminShopProductVariantDto toVariantDto(ShopProductVariant variant) {
        AdminShopProductVariantDto dto = new AdminShopProductVariantDto();
        dto.setId(variant.getId());
        dto.setSku(variant.getSku());
        dto.setVariantLabel(variant.getVariantLabel());
        dto.setColorName(variant.getColorName());
        dto.setColorHex(variant.getColorHex());
        dto.setInternalMaterialCode(variant.getInternalMaterialCode());
        dto.setPriceChf(variant.getPriceChf());
        dto.setIsDefault(variant.getIsDefault());
        dto.setIsActive(variant.getIsActive());
        dto.setSortOrder(variant.getSortOrder());
        dto.setCreatedAt(variant.getCreatedAt());
        dto.setUpdatedAt(variant.getUpdatedAt());
        return dto;
    }

    private ShopProductModelDto toModelDto(ShopProduct product, ShopProductModelAsset asset) {
        if (asset == null) {
            return null;
        }
        return new ShopProductModelDto(
                "/api/admin/shop/products/" + product.getId() + "/model",
                asset.getOriginalFilename(),
                asset.getMimeType(),
                asset.getFileSizeBytes(),
                asset.getBoundingBoxXMm(),
                asset.getBoundingBoxYMm(),
                asset.getBoundingBoxZMm()
        );
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

    private ShopCategory resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required");
        }
        return shopCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
    }

    private void ensurePayload(AdminUpsertShopProductRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required");
        }
    }

    private LocalizedProductContent normalizeLocalizedProductContent(AdminUpsertShopProductRequest payload) {
        String legacyName = normalizeOptional(payload.getName());
        String fallbackName = firstNonBlank(
                legacyName,
                normalizeOptional(payload.getNameIt()),
                normalizeOptional(payload.getNameEn()),
                normalizeOptional(payload.getNameDe()),
                normalizeOptional(payload.getNameFr())
        );
        if (fallbackName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required");
        }

        Map<String, String> names = new LinkedHashMap<>();
        names.put("it", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameIt()), fallbackName), "Italian product name is required"));
        names.put("en", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameEn()), fallbackName), "English product name is required"));
        names.put("de", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameDe()), fallbackName), "German product name is required"));
        names.put("fr", normalizeRequired(firstNonBlank(normalizeOptional(payload.getNameFr()), fallbackName), "French product name is required"));

        String fallbackExcerpt = firstNonBlank(
                normalizeOptional(payload.getExcerpt()),
                normalizeOptional(payload.getExcerptIt()),
                normalizeOptional(payload.getExcerptEn()),
                normalizeOptional(payload.getExcerptDe()),
                normalizeOptional(payload.getExcerptFr())
        );
        Map<String, String> excerpts = new LinkedHashMap<>();
        excerpts.put("it", firstNonBlank(normalizeOptional(payload.getExcerptIt()), fallbackExcerpt));
        excerpts.put("en", firstNonBlank(normalizeOptional(payload.getExcerptEn()), fallbackExcerpt));
        excerpts.put("de", firstNonBlank(normalizeOptional(payload.getExcerptDe()), fallbackExcerpt));
        excerpts.put("fr", firstNonBlank(normalizeOptional(payload.getExcerptFr()), fallbackExcerpt));

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

        return new LocalizedProductContent(
                names.get("it"),
                firstNonBlank(excerpts.get("it"), fallbackExcerpt),
                firstNonBlank(descriptions.get("it"), fallbackDescription),
                names,
                excerpts,
                descriptions
        );
    }

    private void ensureSlugAvailable(String slug, UUID currentProductId) {
        shopProductRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (currentProductId == null || !existing.getId().equals(currentProductId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product slug already exists");
            }
        });
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug is invalid");
        }
        return normalized;
    }

    private String normalizeColorHex(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant colorHex must be in format #RRGGBB");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private void validateModelUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "3D model file is required");
        }
        if (maxModelFileSizeBytes > 0 && file.getSize() > maxModelFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "3D model file exceeds size limit");
        }
        String extension = resolveExtension(sanitizeOriginalFilename(file.getOriginalFilename()));
        if (!SUPPORTED_MODEL_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported 3D model type. Allowed: stl, 3mf");
        }
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int separatorIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String basename = separatorIndex >= 0 ? cleaned.substring(separatorIndex + 1) : cleaned;
        basename = basename.replace("\r", "_").replace("\n", "_");
        return basename.isBlank() ? "model.stl" : basename;
    }

    private String resolveExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildDownloadFilename(String originalFilename) {
        int dotIndex = originalFilename.lastIndexOf('.');
        String base = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        return base + ".stl";
    }

    private String mediaUsageKey(ShopProduct product) {
        return product.getId().toString();
    }

    private void deleteExistingModelFile(ShopProductModelAsset asset, UUID productId) {
        if (asset == null || asset.getStoredRelativePath() == null || asset.getStoredRelativePath().isBlank()) {
            return;
        }
        Path existingPath = shopStorageService.resolveStoredProductPath(asset.getStoredRelativePath(), productId);
        if (existingPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(existingPath);
        } catch (IOException ignored) {
        }
    }

    private void deleteStoredRelativePath(String storedRelativePath, UUID productId, String excludeStoredRelativePath) {
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return;
        }
        if (Objects.equals(storedRelativePath, excludeStoredRelativePath)) {
            return;
        }
        Path existingPath = shopStorageService.resolveStoredProductPath(storedRelativePath, productId);
        deletePathQuietly(existingPath);
    }

    private String computeSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest unavailable", e);
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deletePathQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public record ProductModelDownload(Path path, String filename, String mimeType) {
    }

    private record LocalizedProductContent(
            String defaultName,
            String defaultExcerpt,
            String defaultDescription,
            Map<String, String> names,
            Map<String, String> excerpts,
            Map<String, String> descriptions
    ) {
    }
}

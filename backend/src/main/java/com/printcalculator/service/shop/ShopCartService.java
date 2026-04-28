package com.printcalculator.service.shop;

import com.printcalculator.dto.ShopCartAddItemRequest;
import com.printcalculator.dto.ShopCartUpdateItemRequest;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductModelAsset;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.repository.ShopProductModelAssetRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.quote.QuoteSessionResponseAssembler;
import com.printcalculator.service.quote.QuoteStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ShopCartService {
    private static final String SHOP_CART_SESSION_TYPE = "SHOP_CART";
    private static final String SHOP_LINE_ITEM_TYPE = "SHOP_PRODUCT";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String EXPIRED_STATUS = "EXPIRED";
    private static final String CONVERTED_STATUS = "CONVERTED";

    private final QuoteSessionRepository quoteSessionRepository;
    private final QuoteLineItemRepository quoteLineItemRepository;
    private final ShopProductVariantRepository shopProductVariantRepository;
    private final ShopProductModelAssetRepository shopProductModelAssetRepository;
    private final QuoteSessionTotalsService quoteSessionTotalsService;
    private final QuoteSessionResponseAssembler quoteSessionResponseAssembler;
    private final QuoteStorageService quoteStorageService;
    private final ShopStorageService shopStorageService;
    private final ShopCartCookieService shopCartCookieService;
    private final QuoteSessionExpiryPolicy quoteSessionExpiryPolicy;

    public ShopCartService(
            QuoteSessionRepository quoteSessionRepository,
            QuoteLineItemRepository quoteLineItemRepository,
            ShopProductVariantRepository shopProductVariantRepository,
            ShopProductModelAssetRepository shopProductModelAssetRepository,
            QuoteSessionTotalsService quoteSessionTotalsService,
            QuoteSessionResponseAssembler quoteSessionResponseAssembler,
            QuoteStorageService quoteStorageService,
            ShopStorageService shopStorageService,
            ShopCartCookieService shopCartCookieService,
            QuoteSessionExpiryPolicy quoteSessionExpiryPolicy
    ) {
        this.quoteSessionRepository = quoteSessionRepository;
        this.quoteLineItemRepository = quoteLineItemRepository;
        this.shopProductVariantRepository = shopProductVariantRepository;
        this.shopProductModelAssetRepository = shopProductModelAssetRepository;
        this.quoteSessionTotalsService = quoteSessionTotalsService;
        this.quoteSessionResponseAssembler = quoteSessionResponseAssembler;
        this.quoteStorageService = quoteStorageService;
        this.shopStorageService = shopStorageService;
        this.shopCartCookieService = shopCartCookieService;
        this.quoteSessionExpiryPolicy = quoteSessionExpiryPolicy;
    }

    public CartResult loadCart(HttpServletRequest request) {
        boolean hadCookie = shopCartCookieService.hasCartCookie(request);
        Optional<QuoteSession> session = resolveValidCartSession(request);
        if (session.isEmpty()) {
            return CartResult.empty(quoteSessionResponseAssembler.emptyCart(), hadCookie);
        }

        QuoteSession validSession = session.get();
        touchSession(validSession);
        return CartResult.withSession(buildCartResponse(validSession), validSession.getId(), false);
    }

    @Transactional
    public CartResult addItem(HttpServletRequest request, ShopCartAddItemRequest payload) {
        int quantityToAdd = normalizeQuantity(payload != null ? payload.getQuantity() : null);
        ShopProductVariant variant = getPurchasableVariant(payload != null ? payload.getShopProductVariantId() : null);
        QuoteSession session = resolveValidCartSession(request).orElseGet(this::createCartSession);
        touchSession(session);

        QuoteLineItem lineItem = quoteLineItemRepository
                .findFirstByQuoteSession_IdAndLineItemTypeAndShopProductVariant_Id(
                        session.getId(),
                        SHOP_LINE_ITEM_TYPE,
                        variant.getId()
                )
                .orElseGet(() -> buildShopLineItem(session, variant));

        int existingQuantity = lineItem.getQuantity() != null && lineItem.getQuantity() > 0
                ? lineItem.getQuantity()
                : 0;
        int newQuantity = existingQuantity + quantityToAdd;
        lineItem.setQuantity(newQuantity);
        refreshLineItemSnapshot(lineItem, variant);
        lineItem.setUpdatedAt(OffsetDateTime.now());
        quoteLineItemRepository.save(lineItem);

        return CartResult.withSession(buildCartResponse(session), session.getId(), false);
    }

    @Transactional
    public CartResult updateItem(HttpServletRequest request, UUID lineItemId, ShopCartUpdateItemRequest payload) {
        QuoteSession session = resolveValidCartSession(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart session not found"));

        QuoteLineItem item = quoteLineItemRepository.findByIdAndQuoteSession_Id(lineItemId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));

        if (!SHOP_LINE_ITEM_TYPE.equals(item.getLineItemType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cart item type");
        }

        item.setQuantity(normalizeQuantity(payload != null ? payload.getQuantity() : null));
        item.setUpdatedAt(OffsetDateTime.now());

        if (item.getShopProductVariant() != null) {
            refreshLineItemSnapshot(item, item.getShopProductVariant());
        }

        quoteLineItemRepository.save(item);
        touchSession(session);
        return CartResult.withSession(buildCartResponse(session), session.getId(), false);
    }

    @Transactional
    public CartResult removeItem(HttpServletRequest request, UUID lineItemId) {
        QuoteSession session = resolveValidCartSession(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart session not found"));

        QuoteLineItem item = quoteLineItemRepository.findByIdAndQuoteSession_Id(lineItemId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));

        quoteLineItemRepository.delete(item);
        touchSession(session);
        return CartResult.withSession(buildCartResponse(session), session.getId(), false);
    }

    @Transactional
    public CartResult clearCart(HttpServletRequest request) {
        boolean hadCookie = shopCartCookieService.hasCartCookie(request);
        Optional<QuoteSession> session = resolveValidCartSession(request);
        if (session.isPresent()) {
            QuoteSession current = session.get();
            quoteSessionRepository.delete(current);
        }
        return CartResult.empty(quoteSessionResponseAssembler.emptyCart(), hadCookie);
    }

    private Optional<QuoteSession> resolveValidCartSession(HttpServletRequest request) {
        Optional<UUID> sessionId = shopCartCookieService.extractSessionId(request);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }

        Optional<QuoteSession> session = quoteSessionRepository.findByIdAndSessionType(sessionId.get(), SHOP_CART_SESSION_TYPE);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        QuoteSession quoteSession = session.get();
        if (isSessionUnavailable(quoteSession)) {
            if (!EXPIRED_STATUS.equals(quoteSession.getStatus()) && !CONVERTED_STATUS.equals(quoteSession.getStatus())) {
                quoteSession.setStatus(EXPIRED_STATUS);
                quoteSessionRepository.save(quoteSession);
            }
            return Optional.empty();
        }
        return Optional.of(quoteSession);
    }

    private QuoteSession createCartSession() {
        QuoteSession session = new QuoteSession();
        session.setStatus(ACTIVE_STATUS);
        session.setSessionType(SHOP_CART_SESSION_TYPE);
        session.setPricingVersion("v1");
        session.setMaterialCode("SHOP");
        session.setSupportsEnabled(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(quoteSessionExpiryPolicy.newExpiry());
        session.setSetupCostChf(BigDecimal.ZERO);
        return quoteSessionRepository.save(session);
    }

    private Map<String, Object> buildCartResponse(QuoteSession session) {
        List<QuoteLineItem> items = quoteLineItemRepository.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId());
        QuoteSessionTotalsService.QuoteSessionTotals totals = quoteSessionTotalsService.compute(session, items);
        return quoteSessionResponseAssembler.assemble(session, items, totals);
    }

    private QuoteLineItem buildShopLineItem(QuoteSession session, ShopProductVariant variant) {
        ShopProduct product = variant.getProduct();
        ShopProductModelAsset modelAsset = product != null ? shopProductModelAssetRepository.findByProduct_Id(product.getId()).orElse(null) : null;

        QuoteLineItem item = new QuoteLineItem();
        item.setQuoteSession(session);
        item.setStatus("READY");
        item.setLineItemType(SHOP_LINE_ITEM_TYPE);
        item.setQuantity(0);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        item.setSupportsEnabled(false);
        item.setInfillPercent(0);
        item.setPricingBreakdown(new HashMap<>());

        refreshLineItemSnapshot(item, variant);
        applyModelAssetSnapshot(item, session, modelAsset);
        return item;
    }

    private void refreshLineItemSnapshot(QuoteLineItem item, ShopProductVariant variant) {
        ShopProduct product = variant.getProduct();
        ShopCategory category = product != null ? product.getCategory() : null;

        item.setShopProduct(product);
        item.setShopProductVariant(variant);
        item.setShopProductSlug(product != null ? product.getSlug() : null);
        item.setShopProductName(product != null ? product.getName() : null);
        item.setShopVariantLabel(variant.getVariantLabel());
        item.setShopVariantColorName(variant.getColorName());
        item.setShopVariantColorHex(variant.getColorHex());
        item.setDisplayName(product != null ? product.getName() : item.getDisplayName());
        item.setColorCode(variant.getColorName());
        item.setMaterialCode(variant.getInternalMaterialCode());
        item.setQuality(null);
        item.setUnitPriceChf(variant.getPriceChf() != null ? variant.getPriceChf() : BigDecimal.ZERO);

        Map<String, Object> breakdown = item.getPricingBreakdown() != null
                ? new HashMap<>(item.getPricingBreakdown())
                : new HashMap<>();
        breakdown.put("type", SHOP_LINE_ITEM_TYPE);
        breakdown.put("unitPriceChf", item.getUnitPriceChf());
        item.setPricingBreakdown(breakdown);
    }

    private void applyModelAssetSnapshot(QuoteLineItem item, QuoteSession session, ShopProductModelAsset modelAsset) {
        if (modelAsset == null) {
            if (item.getOriginalFilename() == null || item.getOriginalFilename().isBlank()) {
                item.setOriginalFilename(item.getShopProductSlug() != null ? item.getShopProductSlug() : "shop-product");
            }
            item.setBoundingBoxXMm(BigDecimal.ZERO);
            item.setBoundingBoxYMm(BigDecimal.ZERO);
            item.setBoundingBoxZMm(BigDecimal.ZERO);
            item.setStoredPath(null);
            return;
        }

        item.setOriginalFilename(modelAsset.getOriginalFilename());
        item.setBoundingBoxXMm(modelAsset.getBoundingBoxXMm() != null ? modelAsset.getBoundingBoxXMm() : BigDecimal.ZERO);
        item.setBoundingBoxYMm(modelAsset.getBoundingBoxYMm() != null ? modelAsset.getBoundingBoxYMm() : BigDecimal.ZERO);
        item.setBoundingBoxZMm(modelAsset.getBoundingBoxZMm() != null ? modelAsset.getBoundingBoxZMm() : BigDecimal.ZERO);

        String copiedStoredPath = copyModelAssetIntoSession(session, modelAsset);
        item.setStoredPath(copiedStoredPath);
    }

    private String copyModelAssetIntoSession(QuoteSession session, ShopProductModelAsset modelAsset) {
        if (session == null || modelAsset == null || modelAsset.getProduct() == null) {
            return null;
        }

        Path source = shopStorageService.resolveStoredProductPath(
                modelAsset.getStoredRelativePath(),
                modelAsset.getProduct().getId()
        );
        if (source == null || !Files.exists(source)) {
            return null;
        }

        try {
            Path sessionDir = quoteStorageService.sessionStorageDir(session.getId());
            String extension = quoteStorageService.getSafeExtension(modelAsset.getOriginalFilename(), "stl");
            Path destination = quoteStorageService.resolveSessionPath(
                    sessionDir,
                    UUID.randomUUID() + "." + extension
            );
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return quoteStorageService.toStoredPath(destination);
        } catch (IOException e) {
            return null;
        }
    }

    private ShopProductVariant getPurchasableVariant(UUID variantId) {
        if (variantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopProductVariantId is required");
        }

        ShopProductVariant variant = shopProductVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));

        ShopProduct product = variant.getProduct();
        ShopCategory category = product != null ? product.getCategory() : null;
        if (product == null
                || category == null
                || !Boolean.TRUE.equals(variant.getIsActive())
                || !Boolean.TRUE.equals(product.getIsActive())
                || !Boolean.TRUE.equals(category.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not available");
        }

        return variant;
    }

    private void touchSession(QuoteSession session) {
        session.setStatus(ACTIVE_STATUS);
        session.setExpiresAt(quoteSessionExpiryPolicy.newExpiry());
        quoteSessionRepository.save(session);
    }

    private boolean isSessionUnavailable(QuoteSession session) {
        if (session == null) {
            return true;
        }
        if (!SHOP_CART_SESSION_TYPE.equalsIgnoreCase(session.getSessionType())) {
            return true;
        }
        if (!ACTIVE_STATUS.equalsIgnoreCase(session.getStatus())) {
            return true;
        }
        if (CONVERTED_STATUS.equalsIgnoreCase(session.getStatus())) {
            return true;
        }
        return quoteSessionExpiryPolicy.isExpired(session.getExpiresAt());
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }

    public record CartResult(Map<String, Object> response, UUID sessionId, boolean clearCookie) {
        public static CartResult withSession(Map<String, Object> response, UUID sessionId, boolean clearCookie) {
            return new CartResult(response, sessionId, clearCookie);
        }

        public static CartResult empty(Map<String, Object> response, boolean clearCookie) {
            return new CartResult(response, null, clearCookie);
        }
    }
}

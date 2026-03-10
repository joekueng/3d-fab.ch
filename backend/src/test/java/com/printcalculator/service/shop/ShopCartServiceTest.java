package com.printcalculator.service.shop;

import com.printcalculator.dto.ShopCartAddItemRequest;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.repository.ShopProductModelAssetRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.quote.QuoteSessionResponseAssembler;
import com.printcalculator.service.quote.QuoteStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopCartServiceTest {

    @Mock
    private QuoteSessionRepository quoteSessionRepository;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private ShopProductVariantRepository shopProductVariantRepository;
    @Mock
    private ShopProductModelAssetRepository shopProductModelAssetRepository;
    @Mock
    private QuoteSessionTotalsService quoteSessionTotalsService;
    @Mock
    private QuoteSessionResponseAssembler quoteSessionResponseAssembler;
    @Mock
    private ShopStorageService shopStorageService;
    @Mock
    private ShopCartCookieService shopCartCookieService;

    private ShopCartService service;

    @BeforeEach
    void setUp() {
        service = new ShopCartService(
                quoteSessionRepository,
                quoteLineItemRepository,
                shopProductVariantRepository,
                shopProductModelAssetRepository,
                quoteSessionTotalsService,
                quoteSessionResponseAssembler,
                new QuoteStorageService(),
                shopStorageService,
                shopCartCookieService
        );
    }

    @Test
    void addItem_shouldCreateServerCartAndPersistVariantPricingSnapshot() {
        UUID sessionId = UUID.randomUUID();
        UUID lineItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        List<QuoteLineItem> savedItems = new ArrayList<>();

        ShopProductVariant variant = buildVariant(variantId);

        when(shopCartCookieService.extractSessionId(any())).thenReturn(Optional.empty());
        when(shopCartCookieService.getCookieTtlDays()).thenReturn(30L);
        when(shopProductVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(shopProductModelAssetRepository.findByProduct_Id(variant.getProduct().getId())).thenReturn(Optional.empty());
        when(quoteSessionRepository.save(any(QuoteSession.class))).thenAnswer(invocation -> {
            QuoteSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionId);
            }
            return session;
        });
        when(quoteLineItemRepository.findFirstByQuoteSession_IdAndLineItemTypeAndShopProductVariant_Id(
                eq(sessionId),
                eq("SHOP_PRODUCT"),
                eq(variantId)
        )).thenReturn(Optional.empty());
        when(quoteLineItemRepository.save(any(QuoteLineItem.class))).thenAnswer(invocation -> {
            QuoteLineItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(lineItemId);
            }
            savedItems.clear();
            savedItems.add(item);
            return item;
        });
        when(quoteLineItemRepository.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId)).thenAnswer(invocation -> List.copyOf(savedItems));
        when(quoteSessionTotalsService.compute(any(), any())).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(
                        new BigDecimal("22.80"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("22.80"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("2.00"),
                        new BigDecimal("24.80"),
                        BigDecimal.ZERO
                )
        );
        when(quoteSessionResponseAssembler.assemble(any(), any(), any())).thenAnswer(invocation -> {
            QuoteSession session = invocation.getArgument(0);
            Map<String, Object> response = new HashMap<>();
            response.put("session", session);
            response.put("items", List.of());
            response.put("grandTotalChf", new BigDecimal("24.80"));
            return response;
        });

        ShopCartAddItemRequest payload = new ShopCartAddItemRequest();
        payload.setShopProductVariantId(variantId);
        payload.setQuantity(2);

        ShopCartService.CartResult result = service.addItem(new MockHttpServletRequest(), payload);

        assertEquals(sessionId, result.sessionId());
        assertFalse(result.clearCookie());
        assertEquals(new BigDecimal("24.80"), result.response().get("grandTotalChf"));

        QuoteLineItem savedItem = savedItems.getFirst();
        assertEquals("SHOP_PRODUCT", savedItem.getLineItemType());
        assertEquals("Desk Cable Clip", savedItem.getDisplayName());
        assertEquals("desk-cable-clip", savedItem.getOriginalFilename());
        assertEquals(2, savedItem.getQuantity());
        assertEquals("PLA", savedItem.getMaterialCode());
        assertEquals("Coral Red", savedItem.getColorCode());
        assertEquals("Desk Cable Clip", savedItem.getShopProductName());
        assertEquals("Coral Red", savedItem.getShopVariantLabel());
        assertEquals("Coral Red", savedItem.getShopVariantColorName());
        assertAmountEquals("11.40", savedItem.getUnitPriceChf());
        assertNull(savedItem.getStoredPath());
    }

    @Test
    void loadCart_withExpiredCookieSession_shouldExpireSessionAndAskCookieClear() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setSessionType("SHOP_CART");
        session.setStatus("ACTIVE");
        session.setExpiresAt(OffsetDateTime.now().minusHours(1));

        Map<String, Object> emptyResponse = new HashMap<>();
        emptyResponse.put("session", null);
        emptyResponse.put("items", List.of());

        when(shopCartCookieService.hasCartCookie(any())).thenReturn(true);
        when(shopCartCookieService.extractSessionId(any())).thenReturn(Optional.of(sessionId));
        when(quoteSessionRepository.findByIdAndSessionType(sessionId, "SHOP_CART")).thenReturn(Optional.of(session));
        when(quoteSessionRepository.save(any(QuoteSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quoteSessionResponseAssembler.emptyCart()).thenReturn(emptyResponse);

        ShopCartService.CartResult result = service.loadCart(new MockHttpServletRequest());

        assertTrue(result.clearCookie());
        assertNull(result.sessionId());
        assertEquals(emptyResponse, result.response());
        assertEquals("EXPIRED", session.getStatus());
        verify(quoteSessionRepository).save(session);
    }

    private ShopProductVariant buildVariant(UUID variantId) {
        ShopCategory category = new ShopCategory();
        category.setId(UUID.randomUUID());
        category.setSlug("cable-management");
        category.setName("Cable Management");
        category.setIsActive(true);

        ShopProduct product = new ShopProduct();
        product.setId(UUID.randomUUID());
        product.setCategory(category);
        product.setSlug("desk-cable-clip");
        product.setName("Desk Cable Clip");
        product.setIsActive(true);

        ShopProductVariant variant = new ShopProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSku("DEMO-CLIP-CORAL");
        variant.setVariantLabel("Coral Red");
        variant.setColorName("Coral Red");
        variant.setColorHex("#ff6b6b");
        variant.setInternalMaterialCode("PLA");
        variant.setPriceChf(new BigDecimal("11.40"));
        variant.setIsActive(true);
        variant.setIsDefault(false);
        return variant;
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }
}

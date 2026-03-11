package com.printcalculator.service.shop;

import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopSitemapServiceTest {

    @Mock
    private ShopCategoryRepository shopCategoryRepository;
    @Mock
    private ShopProductRepository shopProductRepository;

    private ShopSitemapService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-11T10:00:00Z"), ZoneOffset.UTC);
        service = new ShopSitemapService(
                shopCategoryRepository,
                shopProductRepository,
                "https://3d-fab.ch/",
                900,
                fixedClock
        );
    }

    @Test
    void getShopSitemapXml_shouldGenerateLocalizedCategoryAndProductEntries() {
        ShopCategory visibleCategory = new ShopCategory();
        visibleCategory.setId(UUID.fromString("21111111-1111-1111-1111-111111111111"));
        visibleCategory.setSlug("accessori");
        visibleCategory.setIndexable(true);
        visibleCategory.setIsActive(true);
        visibleCategory.setUpdatedAt(OffsetDateTime.parse("2026-03-10T08:00:00Z"));

        ShopCategory hiddenCategory = new ShopCategory();
        hiddenCategory.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        hiddenCategory.setSlug("bozza");
        hiddenCategory.setIndexable(false);
        hiddenCategory.setIsActive(true);
        hiddenCategory.setUpdatedAt(OffsetDateTime.parse("2026-03-10T09:00:00Z"));

        ShopProduct indexedProduct = new ShopProduct();
        indexedProduct.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        indexedProduct.setCategory(visibleCategory);
        indexedProduct.setSlug("supporto-bici");
        indexedProduct.setNameIt("Supporto bici");
        indexedProduct.setNameEn("Bike Holder");
        indexedProduct.setNameDe("Fahrrad Halter");
        indexedProduct.setNameFr("Support velo");
        indexedProduct.setIndexable(true);
        indexedProduct.setIsActive(true);
        indexedProduct.setUpdatedAt(OffsetDateTime.parse("2026-03-11T07:30:00Z"));

        ShopProduct hiddenProduct = new ShopProduct();
        hiddenProduct.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        hiddenProduct.setCategory(visibleCategory);
        hiddenProduct.setSlug("draft");
        hiddenProduct.setIndexable(false);
        hiddenProduct.setIsActive(true);
        hiddenProduct.setUpdatedAt(OffsetDateTime.parse("2026-03-11T08:00:00Z"));

        when(shopCategoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(visibleCategory, hiddenCategory));
        when(shopProductRepository.findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc())
                .thenReturn(List.of(indexedProduct, hiddenProduct));

        String xml = service.getShopSitemapXml();

        assertTrue(xml.contains("<loc>https://3d-fab.ch/it/shop/accessori</loc>"));
        assertTrue(xml.contains("hreflang=\"en\" href=\"https://3d-fab.ch/en/shop/accessori\""));
        assertFalse(xml.contains("https://3d-fab.ch/it/shop/bozza"));

        assertTrue(xml.contains("<loc>https://3d-fab.ch/it/shop/p/123e4567-supporto-bici</loc>"));
        assertTrue(xml.contains("hreflang=\"en\" href=\"https://3d-fab.ch/en/shop/p/123e4567-bike-holder\""));
        assertTrue(xml.contains("hreflang=\"de\" href=\"https://3d-fab.ch/de/shop/p/123e4567-fahrrad-halter\""));
        assertTrue(xml.contains("hreflang=\"x-default\" href=\"https://3d-fab.ch/it/shop/p/123e4567-supporto-bici\""));
        assertTrue(xml.contains("<lastmod>2026-03-11T07:30:00Z</lastmod>"));
        assertFalse(xml.contains("33333333-draft"));
    }

    @Test
    void getShopSitemapXml_shouldServeCachedPayloadWithinTtl() {
        when(shopCategoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of());
        when(shopProductRepository.findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc()).thenReturn(List.of());

        String firstXml = service.getShopSitemapXml();
        String secondXml = service.getShopSitemapXml();

        assertTrue(firstXml.contains("<urlset"));
        assertTrue(secondXml.contains("<urlset"));
        verify(shopCategoryRepository, times(1)).findAllByIsActiveTrueOrderBySortOrderAscNameAsc();
        verify(shopProductRepository, times(1)).findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc();
    }
}

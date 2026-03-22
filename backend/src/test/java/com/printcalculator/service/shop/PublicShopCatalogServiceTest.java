package com.printcalculator.service.shop;

import com.printcalculator.dto.ShopProductCatalogResponseDto;
import com.printcalculator.dto.ShopProductDetailDto;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.ShopCategoryRepository;
import com.printcalculator.repository.ShopProductModelAssetRepository;
import com.printcalculator.repository.ShopProductRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicShopCatalogServiceTest {

    @Mock
    private ShopCategoryRepository shopCategoryRepository;
    @Mock
    private ShopProductRepository shopProductRepository;
    @Mock
    private ShopProductVariantRepository shopProductVariantRepository;
    @Mock
    private ShopProductModelAssetRepository shopProductModelAssetRepository;
    @Mock
    private FilamentVariantRepository filamentVariantRepository;
    @Mock
    private PublicMediaQueryService publicMediaQueryService;
    @Mock
    private ShopStorageService shopStorageService;

    private PublicShopCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PublicShopCatalogService(
                shopCategoryRepository,
                shopProductRepository,
                shopProductVariantRepository,
                shopProductModelAssetRepository,
                filamentVariantRepository,
                publicMediaQueryService,
                shopStorageService
        );
    }

    @Test
    void getProductCatalog_shouldExposePublicPathAsSegment() {
        ShopCategory category = buildCategory();
        ShopProduct product = buildProduct(category);
        ShopProductVariant variant = buildVariant(product);

        stubPublicCatalog(category, product, variant);

        ShopProductCatalogResponseDto response = service.getProductCatalog(null, false, "en");

        assertEquals(1, response.products().size());
        assertEquals("12345678-bike-wall-hanger", response.products().getFirst().publicPath());
        assertEquals("/en/shop/p/12345678-bike-wall-hanger", response.products().getFirst().localizedPaths().get("en"));
        assertEquals("/it/shop/p/12345678-supporto-bici", response.products().getFirst().localizedPaths().get("it"));
    }

    @Test
    void getProduct_shouldExposePublicPathAsSegment() {
        ShopCategory category = buildCategory();
        ShopProduct product = buildProduct(category);
        ShopProductVariant variant = buildVariant(product);

        stubPublicCatalog(category, product, variant);

        ShopProductDetailDto response = service.getProduct("bike-wall-hanger", "en");

        assertEquals("12345678-bike-wall-hanger", response.publicPath());
        assertEquals("/en/shop/p/12345678-bike-wall-hanger", response.localizedPaths().get("en"));
        assertEquals("/it/shop/p/12345678-supporto-bici", response.localizedPaths().get("it"));
    }

    private void stubPublicCatalog(ShopCategory category, ShopProduct product, ShopProductVariant variant) {
        when(shopCategoryRepository.findAllByIsActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(category));
        when(shopProductRepository.findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc()).thenReturn(List.of(product));
        when(shopProductVariantRepository.findByProduct_IdInAndIsActiveTrueOrderBySortOrderAscColorNameAsc(anyList()))
                .thenReturn(List.of(variant));
        when(shopProductModelAssetRepository.findByProduct_IdIn(anyList())).thenReturn(List.of());
        when(filamentVariantRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(publicMediaQueryService.getUsageMediaMap(anyString(), anyList(), anyString())).thenReturn(Map.of());
    }

    private ShopCategory buildCategory() {
        ShopCategory category = new ShopCategory();
        category.setId(UUID.fromString("21111111-1111-1111-1111-111111111111"));
        category.setSlug("accessori");
        category.setName("Accessori");
        category.setNameIt("Accessori");
        category.setNameEn("Accessories");
        category.setIsActive(true);
        category.setSortOrder(0);
        return category;
    }

    private ShopProduct buildProduct(ShopCategory category) {
        ShopProduct product = new ShopProduct();
        product.setId(UUID.fromString("12345678-abcd-4abc-9abc-1234567890ab"));
        product.setCategory(category);
        product.setSlug("bike-wall-hanger");
        product.setName("Bike Wall-Hanger");
        product.setNameIt("Supporto bici");
        product.setNameEn("Bike Wall-Hanger");
        product.setIsActive(true);
        product.setIsFeatured(true);
        product.setSortOrder(0);
        return product;
    }

    private ShopProductVariant buildVariant(ShopProduct product) {
        ShopProductVariant variant = new ShopProductVariant();
        variant.setId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
        variant.setProduct(product);
        variant.setVariantLabel("PLA");
        variant.setColorName("Grigio");
        variant.setInternalMaterialCode("PLA");
        variant.setPriceChf(new BigDecimal("29.90"));
        variant.setIsActive(true);
        variant.setIsDefault(true);
        variant.setSortOrder(0);
        return variant;
    }
}

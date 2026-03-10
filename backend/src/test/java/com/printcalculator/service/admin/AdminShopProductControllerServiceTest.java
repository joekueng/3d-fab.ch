package com.printcalculator.service.admin;

import com.printcalculator.entity.ShopProduct;
import com.printcalculator.entity.ShopProductModelAsset;
import com.printcalculator.entity.ShopProductVariant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminShopProductControllerServiceTest {

    @Mock
    private ShopProductRepository shopProductRepository;
    @Mock
    private ShopCategoryRepository shopCategoryRepository;
    @Mock
    private ShopProductVariantRepository shopProductVariantRepository;
    @Mock
    private ShopProductModelAssetRepository shopProductModelAssetRepository;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PublicMediaQueryService publicMediaQueryService;
    @Mock
    private AdminMediaControllerService adminMediaControllerService;
    @Mock
    private ShopStorageService shopStorageService;
    @Mock
    private SlicerService slicerService;
    @Mock
    private ClamAVService clamAVService;

    private AdminShopProductControllerService service;

    @BeforeEach
    void setUp() {
        service = new AdminShopProductControllerService(
                shopProductRepository,
                shopCategoryRepository,
                shopProductVariantRepository,
                shopProductModelAssetRepository,
                quoteLineItemRepository,
                orderItemRepository,
                publicMediaQueryService,
                adminMediaControllerService,
                shopStorageService,
                slicerService,
                clamAVService,
                104857600L
        );
    }

    @Test
    void deleteProduct_shouldDeleteManagedDependenciesBeforeDeletingProduct() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        ShopProduct product = new ShopProduct();
        product.setId(productId);

        ShopProductVariant variant = new ShopProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);

        ShopProductModelAsset asset = new ShopProductModelAsset();
        asset.setId(UUID.randomUUID());
        asset.setProduct(product);
        asset.setStoredRelativePath("products/" + productId + "/model.stl");

        when(shopProductRepository.findById(productId)).thenReturn(Optional.of(product));
        when(quoteLineItemRepository.existsByShopProduct_Id(productId)).thenReturn(false);
        when(orderItemRepository.existsByShopProduct_Id(productId)).thenReturn(false);
        when(shopProductVariantRepository.findByProduct_IdOrderBySortOrderAscColorNameAsc(productId)).thenReturn(List.of(variant));
        when(quoteLineItemRepository.existsByShopProductVariant_Id(variantId)).thenReturn(false);
        when(orderItemRepository.existsByShopProductVariant_Id(variantId)).thenReturn(false);
        when(shopProductModelAssetRepository.findByProduct_Id(productId)).thenReturn(Optional.of(asset));
        when(shopStorageService.resolveStoredProductPath(asset.getStoredRelativePath(), productId))
                .thenReturn(Path.of("/tmp/shop-model.stl"));

        service.deleteProduct(productId);

        InOrder inOrder = inOrder(shopProductModelAssetRepository, shopProductVariantRepository, shopProductRepository);
        inOrder.verify(shopProductModelAssetRepository).delete(asset);
        inOrder.verify(shopProductVariantRepository).deleteAll(List.of(variant));
        inOrder.verify(shopProductRepository).delete(product);

        verify(shopStorageService).resolveStoredProductPath(asset.getStoredRelativePath(), productId);
    }

    @Test
    void deleteProduct_shouldSkipDependencyDeletesWhenNothingIsAttached() {
        UUID productId = UUID.randomUUID();

        ShopProduct product = new ShopProduct();
        product.setId(productId);

        when(shopProductRepository.findById(productId)).thenReturn(Optional.of(product));
        when(quoteLineItemRepository.existsByShopProduct_Id(productId)).thenReturn(false);
        when(orderItemRepository.existsByShopProduct_Id(productId)).thenReturn(false);
        when(shopProductVariantRepository.findByProduct_IdOrderBySortOrderAscColorNameAsc(productId)).thenReturn(List.of());
        when(shopProductModelAssetRepository.findByProduct_Id(productId)).thenReturn(Optional.empty());

        service.deleteProduct(productId);

        verify(shopProductRepository).delete(product);
        verify(shopProductVariantRepository, never()).deleteAll(any());
        verify(shopProductModelAssetRepository, never()).delete(any());
    }
}

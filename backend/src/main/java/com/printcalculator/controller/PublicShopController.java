package com.printcalculator.controller;

import com.printcalculator.dto.ShopCategoryDetailDto;
import com.printcalculator.dto.ShopCategoryTreeDto;
import com.printcalculator.dto.ShopProductCatalogResponseDto;
import com.printcalculator.dto.ShopProductDetailDto;
import com.printcalculator.service.shop.PublicShopCatalogService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/shop")
@Transactional(readOnly = true)
public class PublicShopController {
    private final PublicShopCatalogService publicShopCatalogService;

    public PublicShopController(PublicShopCatalogService publicShopCatalogService) {
        this.publicShopCatalogService = publicShopCatalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<ShopCategoryTreeDto>> getCategories(@RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicShopCatalogService.getCategories(lang));
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<ShopCategoryDetailDto> getCategory(@PathVariable String slug,
                                                             @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicShopCatalogService.getCategory(slug, lang));
    }

    @GetMapping("/products")
    public ResponseEntity<ShopProductCatalogResponseDto> getProducts(
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String lang
    ) {
        return ResponseEntity.ok(publicShopCatalogService.getProductCatalog(categorySlug, featured, lang));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ShopProductDetailDto> getProduct(@PathVariable String slug,
                                                           @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicShopCatalogService.getProduct(slug, lang));
    }

    @GetMapping("/products/by-path/{publicPath}")
    public ResponseEntity<ShopProductDetailDto> getProductByPublicPath(@PathVariable String publicPath,
                                                                       @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicShopCatalogService.getProductByPublicPath(publicPath, lang));
    }

    @GetMapping("/products/by-id-prefix/{idPrefix}")
    public ResponseEntity<ShopProductDetailDto> getProductByIdPrefix(@PathVariable String idPrefix,
                                                                     @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(publicShopCatalogService.getProductByIdPrefix(idPrefix, lang));
    }

    @GetMapping("/products/{slug}/model")
    public ResponseEntity<Resource> getProductModel(@PathVariable String slug) throws IOException {
        PublicShopCatalogService.ProductModelDownload model = publicShopCatalogService.getProductModelDownload(slug);
        Resource resource = new UrlResource(model.path().toUri());
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (model.mimeType() != null && !model.mimeType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(model.mimeType());
            } catch (IllegalArgumentException ignored) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + model.filename() + "\"")
                .body(resource);
    }
}

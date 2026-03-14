package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminShopProductDto;
import com.printcalculator.dto.AdminTranslateShopProductRequest;
import com.printcalculator.dto.AdminTranslateShopProductResponse;
import com.printcalculator.dto.AdminUpsertShopProductRequest;
import com.printcalculator.service.admin.AdminShopProductControllerService;
import com.printcalculator.service.admin.AdminShopProductTranslationService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shop/products")
@Transactional(readOnly = true)
public class AdminShopProductController {
    private final AdminShopProductControllerService adminShopProductControllerService;
    private final AdminShopProductTranslationService adminShopProductTranslationService;

    public AdminShopProductController(AdminShopProductControllerService adminShopProductControllerService,
                                      AdminShopProductTranslationService adminShopProductTranslationService) {
        this.adminShopProductControllerService = adminShopProductControllerService;
        this.adminShopProductTranslationService = adminShopProductTranslationService;
    }

    @GetMapping
    public ResponseEntity<List<AdminShopProductDto>> getProducts() {
        return ResponseEntity.ok(adminShopProductControllerService.getProducts());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<AdminShopProductDto> getProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(adminShopProductControllerService.getProduct(productId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminShopProductDto> createProduct(@RequestBody AdminUpsertShopProductRequest payload) {
        return ResponseEntity.ok(adminShopProductControllerService.createProduct(payload));
    }

    @PostMapping("/translate")
    public ResponseEntity<AdminTranslateShopProductResponse> translateProduct(@RequestBody AdminTranslateShopProductRequest payload) {
        return ResponseEntity.ok(adminShopProductTranslationService.translateProduct(payload));
    }

    @PutMapping("/{productId}")
    @Transactional
    public ResponseEntity<AdminShopProductDto> updateProduct(@PathVariable UUID productId,
                                                             @RequestBody AdminUpsertShopProductRequest payload) {
        return ResponseEntity.ok(adminShopProductControllerService.updateProduct(productId, payload));
    }

    @DeleteMapping("/{productId}")
    @Transactional
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID productId) {
        adminShopProductControllerService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/model")
    @Transactional
    public ResponseEntity<AdminShopProductDto> uploadProductModel(@PathVariable UUID productId,
                                                                  @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(adminShopProductControllerService.uploadProductModel(productId, file));
    }

    @DeleteMapping("/{productId}/model")
    @Transactional
    public ResponseEntity<Void> deleteProductModel(@PathVariable UUID productId) {
        adminShopProductControllerService.deleteProductModel(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/model")
    public ResponseEntity<Resource> getProductModel(@PathVariable UUID productId) throws IOException {
        AdminShopProductControllerService.ProductModelDownload model = adminShopProductControllerService.getProductModel(productId);
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

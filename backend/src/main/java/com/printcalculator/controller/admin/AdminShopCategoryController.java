package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminShopCategoryDto;
import com.printcalculator.dto.AdminUpsertShopCategoryRequest;
import com.printcalculator.service.admin.AdminShopCategoryControllerService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/shop/categories")
@Transactional(readOnly = true)
public class AdminShopCategoryController {
    private final AdminShopCategoryControllerService adminShopCategoryControllerService;

    public AdminShopCategoryController(AdminShopCategoryControllerService adminShopCategoryControllerService) {
        this.adminShopCategoryControllerService = adminShopCategoryControllerService;
    }

    @GetMapping
    public ResponseEntity<List<AdminShopCategoryDto>> getCategories() {
        return ResponseEntity.ok(adminShopCategoryControllerService.getCategories());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<AdminShopCategoryDto>> getCategoryTree() {
        return ResponseEntity.ok(adminShopCategoryControllerService.getCategoryTree());
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<AdminShopCategoryDto> getCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(adminShopCategoryControllerService.getCategory(categoryId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminShopCategoryDto> createCategory(@RequestBody AdminUpsertShopCategoryRequest payload) {
        return ResponseEntity.ok(adminShopCategoryControllerService.createCategory(payload));
    }

    @PutMapping("/{categoryId}")
    @Transactional
    public ResponseEntity<AdminShopCategoryDto> updateCategory(@PathVariable UUID categoryId,
                                                               @RequestBody AdminUpsertShopCategoryRequest payload) {
        return ResponseEntity.ok(adminShopCategoryControllerService.updateCategory(categoryId, payload));
    }

    @DeleteMapping("/{categoryId}")
    @Transactional
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID categoryId) {
        adminShopCategoryControllerService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}

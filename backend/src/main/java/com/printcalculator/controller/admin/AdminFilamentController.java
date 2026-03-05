package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminFilamentMaterialTypeDto;
import com.printcalculator.dto.AdminFilamentVariantDto;
import com.printcalculator.dto.AdminUpsertFilamentMaterialTypeRequest;
import com.printcalculator.dto.AdminUpsertFilamentVariantRequest;
import com.printcalculator.service.admin.AdminFilamentControllerService;
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

@RestController
@RequestMapping("/api/admin/filaments")
@Transactional(readOnly = true)
public class AdminFilamentController {

    private final AdminFilamentControllerService adminFilamentControllerService;

    public AdminFilamentController(AdminFilamentControllerService adminFilamentControllerService) {
        this.adminFilamentControllerService = adminFilamentControllerService;
    }

    @GetMapping("/materials")
    public ResponseEntity<List<AdminFilamentMaterialTypeDto>> getMaterials() {
        return ResponseEntity.ok(adminFilamentControllerService.getMaterials());
    }

    @GetMapping("/variants")
    public ResponseEntity<List<AdminFilamentVariantDto>> getVariants() {
        return ResponseEntity.ok(adminFilamentControllerService.getVariants());
    }

    @PostMapping("/materials")
    @Transactional
    public ResponseEntity<AdminFilamentMaterialTypeDto> createMaterial(
            @RequestBody AdminUpsertFilamentMaterialTypeRequest payload
    ) {
        return ResponseEntity.ok(adminFilamentControllerService.createMaterial(payload));
    }

    @PutMapping("/materials/{materialTypeId}")
    @Transactional
    public ResponseEntity<AdminFilamentMaterialTypeDto> updateMaterial(
            @PathVariable Long materialTypeId,
            @RequestBody AdminUpsertFilamentMaterialTypeRequest payload
    ) {
        return ResponseEntity.ok(adminFilamentControllerService.updateMaterial(materialTypeId, payload));
    }

    @PostMapping("/variants")
    @Transactional
    public ResponseEntity<AdminFilamentVariantDto> createVariant(
            @RequestBody AdminUpsertFilamentVariantRequest payload
    ) {
        return ResponseEntity.ok(adminFilamentControllerService.createVariant(payload));
    }

    @PutMapping("/variants/{variantId}")
    @Transactional
    public ResponseEntity<AdminFilamentVariantDto> updateVariant(
            @PathVariable Long variantId,
            @RequestBody AdminUpsertFilamentVariantRequest payload
    ) {
        return ResponseEntity.ok(adminFilamentControllerService.updateVariant(variantId, payload));
    }

    @DeleteMapping("/variants/{variantId}")
    @Transactional
    public ResponseEntity<Void> deleteVariant(@PathVariable Long variantId) {
        adminFilamentControllerService.deleteVariant(variantId);
        return ResponseEntity.noContent().build();
    }
}

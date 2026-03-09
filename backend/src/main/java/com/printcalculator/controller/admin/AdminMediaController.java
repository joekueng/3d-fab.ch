package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminCreateMediaUsageRequest;
import com.printcalculator.dto.AdminMediaAssetDto;
import com.printcalculator.dto.AdminMediaUsageDto;
import com.printcalculator.dto.AdminUpdateMediaAssetRequest;
import com.printcalculator.dto.AdminUpdateMediaUsageRequest;
import com.printcalculator.service.admin.AdminMediaControllerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/media")
@Transactional(readOnly = true)
public class AdminMediaController {

    private final AdminMediaControllerService adminMediaControllerService;

    public AdminMediaController(AdminMediaControllerService adminMediaControllerService) {
        this.adminMediaControllerService = adminMediaControllerService;
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<AdminMediaAssetDto> uploadAsset(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "title", required = false) String title,
                                                          @RequestParam(value = "altText", required = false) String altText,
                                                          @RequestParam(value = "visibility", required = false) String visibility) {
        return ResponseEntity.ok(adminMediaControllerService.uploadAsset(file, title, altText, visibility));
    }

    @GetMapping("/assets")
    public ResponseEntity<List<AdminMediaAssetDto>> listAssets() {
        return ResponseEntity.ok(adminMediaControllerService.listAssets());
    }

    @GetMapping("/assets/{mediaAssetId}")
    public ResponseEntity<AdminMediaAssetDto> getAsset(@PathVariable UUID mediaAssetId) {
        return ResponseEntity.ok(adminMediaControllerService.getAsset(mediaAssetId));
    }

    @GetMapping("/usages")
    public ResponseEntity<List<AdminMediaUsageDto>> getUsages(@RequestParam String usageType,
                                                              @RequestParam String usageKey,
                                                              @RequestParam(required = false) UUID ownerId) {
        return ResponseEntity.ok(adminMediaControllerService.getUsages(usageType, usageKey, ownerId));
    }

    @PatchMapping("/assets/{mediaAssetId}")
    @Transactional
    public ResponseEntity<AdminMediaAssetDto> updateAsset(@PathVariable UUID mediaAssetId,
                                                          @RequestBody AdminUpdateMediaAssetRequest payload) {
        return ResponseEntity.ok(adminMediaControllerService.updateAsset(mediaAssetId, payload));
    }

    @PostMapping("/usages")
    @Transactional
    public ResponseEntity<AdminMediaUsageDto> createUsage(@RequestBody AdminCreateMediaUsageRequest payload) {
        return ResponseEntity.ok(adminMediaControllerService.createUsage(payload));
    }

    @PatchMapping("/usages/{mediaUsageId}")
    @Transactional
    public ResponseEntity<AdminMediaUsageDto> updateUsage(@PathVariable UUID mediaUsageId,
                                                          @RequestBody AdminUpdateMediaUsageRequest payload) {
        return ResponseEntity.ok(adminMediaControllerService.updateUsage(mediaUsageId, payload));
    }

    @DeleteMapping("/usages/{mediaUsageId}")
    @Transactional
    public ResponseEntity<Void> deleteUsage(@PathVariable UUID mediaUsageId) {
        adminMediaControllerService.deleteUsage(mediaUsageId);
        return ResponseEntity.noContent().build();
    }
}

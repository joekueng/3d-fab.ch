package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminTranslateLocalizedTextRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextResponse;
import com.printcalculator.service.admin.AdminLocalizedTextTranslationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/translations")
@Transactional(readOnly = true)
public class AdminTranslationController {

    private final AdminLocalizedTextTranslationService localizedTextTranslationService;

    public AdminTranslationController(AdminLocalizedTextTranslationService localizedTextTranslationService) {
        this.localizedTextTranslationService = localizedTextTranslationService;
    }

    @PostMapping("/localized-text")
    public ResponseEntity<AdminTranslateLocalizedTextResponse> translateLocalizedText(
            @Valid @RequestBody AdminTranslateLocalizedTextRequest payload) {
        return ResponseEntity.ok(localizedTextTranslationService.translateLocalizedText(payload));
    }
}

package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminLocalizedTextFieldRequest;
import com.printcalculator.dto.AdminTranslateFilamentColorsResponse;
import com.printcalculator.dto.AdminTranslateLocalizedTextRequest;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AdminFilamentColorTranslationService {
    private static final int BATCH_SIZE = 25;
    private final FilamentVariantRepository filamentRepository;
    private final ShopProductVariantRepository shopRepository;
    private final AdminLocalizedTextTranslationService translations;

    public AdminFilamentColorTranslationService(FilamentVariantRepository filamentRepository,
                                                ShopProductVariantRepository shopRepository,
                                                AdminLocalizedTextTranslationService translations) {
        this.filamentRepository = filamentRepository;
        this.shopRepository = shopRepository;
        this.translations = translations;
    }

    @Transactional
    public AdminTranslateFilamentColorsResponse translateColors() {
        List<FilamentVariant> filaments = filamentRepository.findAll();
        List<ShopProductVariant> shopVariants = shopRepository.findAll();
        Map<ColorKey, String> filamentSources = new LinkedHashMap<>();
        Map<FilamentVariant, String> filamentLabels = new LinkedHashMap<>();
        Map<ShopProductVariant, String> shopLabels = new LinkedHashMap<>();
        for (FilamentVariant filament : filaments) {
            String source = italianSource(filament.getColorLabelIt(), filament.getColorName());
            filamentLabels.put(filament, source);
            String material = filament.getFilamentMaterialType() != null
                    ? filament.getFilamentMaterialType().getMaterialCode() : "";
            filamentSources.putIfAbsent(colorKey(material, filament.getColorName(), filament.getColorHex()), source);
        }
        for (ShopProductVariant variant : shopVariants) {
            String source = filamentSources.get(colorKey(
                    variant.getInternalMaterialCode(), variant.getColorName(), variant.getColorHex()));
            shopLabels.put(variant, source != null ? source : italianSource(variant.getColorLabelIt(), variant.getColorName()));
        }

        // Translate each distinct Italian label once, even when many products use that color.
        Map<String, Map<String, String>> translated = new LinkedHashMap<>();
        filamentLabels.values().forEach(label -> translated.putIfAbsent(label, Map.of()));
        shopLabels.values().forEach(label -> translated.putIfAbsent(label, Map.of()));
        List<String> sources = new ArrayList<>(translated.keySet());
        for (int start = 0; start < sources.size(); start += BATCH_SIZE) {
            List<String> batch = sources.subList(start, Math.min(start + BATCH_SIZE, sources.size()));
            AdminTranslateLocalizedTextRequest request = new AdminTranslateLocalizedTextRequest();
            request.setContext("3D printing filament color names. Translate the Italian color labels faithfully, including finishes. Preserve brand names and technical codes.");
            request.setSourceLanguage("it");
            request.setOverwriteExisting(true);
            Map<String, AdminLocalizedTextFieldRequest> fields = new LinkedHashMap<>();
            for (int index = 0; index < batch.size(); index++) {
                AdminLocalizedTextFieldRequest field = new AdminLocalizedTextFieldRequest();
                field.setRequired(true);
                field.setValues(Map.of("it", batch.get(index), "en", "", "de", "", "fr", ""));
                fields.put("color_" + index, field);
            }
            request.setFields(fields);
            var response = translations.translateLocalizedText(request);
            for (int index = 0; index < batch.size(); index++) {
                Map<String, String> labels = response.getFields().get("color_" + index);
                if (labels == null || List.of("en", "de", "fr").stream()
                        .anyMatch(language -> labels.getOrDefault(language, "").isBlank())) {
                    throw new ResponseStatusException(BAD_GATEWAY, "Incomplete color translations");
                }
                translated.put(batch.get(index), labels);
            }
        }

        // Apply only after every batch succeeds, and commit filament/shop labels together.
        filamentLabels.forEach((variant, source) -> {
            Map<String, String> labels = translated.get(source);
            ensureUpdated(filamentRepository.updateColorTranslations(variant.getId(),
                    variant.getColorLabelIt() == null ? "" : variant.getColorLabelIt(), variant.getColorName(),
                    labels.get("en").trim(), labels.get("de").trim(), labels.get("fr").trim()));
        });
        shopLabels.forEach((variant, source) -> {
            Map<String, String> labels = translated.get(source);
            ensureUpdated(shopRepository.updateColorTranslations(variant.getId(),
                    variant.getColorLabelIt() == null ? "" : variant.getColorLabelIt(), variant.getColorName(),
                    labels.get("en").trim(), labels.get("de").trim(), labels.get("fr").trim()));
        });
        return new AdminTranslateFilamentColorsResponse(sources.size(), filaments.size(), shopVariants.size());
    }

    private void ensureUpdated(int count) {
        if (count != 1) {
            throw new ResponseStatusException(CONFLICT, "A color changed during translation. Please refresh and retry.");
        }
    }

    private String italianSource(String italian, String colorName) {
        String source = italian != null && !italian.isBlank() ? italian : colorName;
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "An Italian color label is required for every color");
        }
        return source.trim();
    }

    private ColorKey colorKey(String material, String name, String hex) {
        return new ColorKey(normalize(material), normalize(name), normalize(hex));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ColorKey(String material, String name, String hex) {
    }
}

package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminTranslateLocalizedTextRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.ShopProductVariant;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.ShopProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminFilamentColorTranslationServiceTest {
    @Mock
    FilamentVariantRepository filaments;
    @Mock
    ShopProductVariantRepository shop;
    @Mock
    AdminLocalizedTextTranslationService translations;
    @InjectMocks
    AdminFilamentColorTranslationService service;

    @Test
    void overwritesAllTargetLanguagesAndSynchronizesExistingShopColors() {
        FilamentVariant filament = filament("Nero opaco");
        FilamentVariant duplicate = filament("Nero opaco");
        ShopProductVariant productColor = new ShopProductVariant();
        productColor.setInternalMaterialCode("pla");
        productColor.setColorName(" BLACK ");
        productColor.setColorHex("#AABBCC");
        productColor.setColorLabelIt("Vecchia etichetta shop");
        productColor.setColorLabelEn("old shop English");
        when(filaments.findAll()).thenReturn(List.of(filament, duplicate));
        when(shop.findAll()).thenReturn(List.of(productColor));
        when(filaments.updateColorTranslations(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(shop.updateColorTranslations(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(translations.translateLocalizedText(any())).thenAnswer(invocation -> {
            AdminTranslateLocalizedTextRequest request = invocation.getArgument(0);
            assertEquals("it", request.getSourceLanguage());
            assertTrue(request.getOverwriteExisting());
            assertEquals(1, request.getFields().size());
            assertEquals("Nero opaco", request.getFields().get("color_0").getValues().get("it"));
            return response(request);
        });

        var result = service.translateColors();

        assertEquals(1, result.translatedColors());
        assertEquals(2, result.filamentVariants());
        assertEquals(1, result.shopVariants());
        verify(filaments, times(2)).updateColorTranslations(null, "Nero opaco", "Black",
                "en:Nero opaco", "de:Nero opaco", "fr:Nero opaco");
        verify(shop).updateColorTranslations(null, "Vecchia etichetta shop", " BLACK ",
                "en:Nero opaco", "de:Nero opaco", "fr:Nero opaco");
        assertEquals("Nero opaco", filament.getColorLabelIt());
        assertEquals("Vecchia etichetta shop", productColor.getColorLabelIt());
        assertEquals("Black", filament.getColorName());
        assertFalse(filament.getIsActive()); // Inactive colors are also translated.
    }

    @Test
    void translatesShopOnlyColorsAndFallsBackToColorNameForLegacyRecords() {
        ShopProductVariant productColor = new ShopProductVariant();
        productColor.setColorName("Corallo");
        when(filaments.findAll()).thenReturn(List.of());
        when(shop.findAll()).thenReturn(List.of(productColor));
        when(shop.updateColorTranslations(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(translations.translateLocalizedText(any())).thenAnswer(invocation -> response(invocation.getArgument(0)));
        assertEquals(1, service.translateColors().translatedColors());
        verify(shop).updateColorTranslations(null, "", "Corallo", "en:Corallo", "de:Corallo", "fr:Corallo");
        assertNull(productColor.getColorLabelIt());
    }

    @Test
    void keepsEveryRecordUnchangedWhenALaterBatchFails() {
        List<FilamentVariant> colors = IntStream.range(0, 26).mapToObj(index -> filament("Colore " + index)).toList();
        when(filaments.findAll()).thenReturn(colors);
        when(shop.findAll()).thenReturn(List.of());
        when(translations.translateLocalizedText(any()))
                .thenAnswer(invocation -> response(invocation.getArgument(0)))
                .thenThrow(new IllegalStateException("translation unavailable"));
        assertThrows(IllegalStateException.class, () -> service.translateColors());
        colors.forEach(color -> assertEquals("old English", color.getColorLabelEn()));
        verify(filaments, never()).updateColorTranslations(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsIncompleteTranslationsWithoutOverwritingExistingLabels() {
        FilamentVariant color = filament("Nero");
        when(filaments.findAll()).thenReturn(List.of(color));
        when(shop.findAll()).thenReturn(List.of());
        AdminTranslateLocalizedTextResponse response = new AdminTranslateLocalizedTextResponse();
        response.setFields(Map.of("color_0", Map.of("en", "Black")));
        when(translations.translateLocalizedText(any())).thenReturn(response);
        assertThrows(ResponseStatusException.class, () -> service.translateColors());
        assertEquals("old English", color.getColorLabelEn());
    }

    @Test
    void rejectsAColorWhoseItalianSourceChangedWhileTranslating() {
        when(filaments.findAll()).thenReturn(List.of(filament("Nero")));
        when(shop.findAll()).thenReturn(List.of());
        when(translations.translateLocalizedText(any())).thenAnswer(invocation -> response(invocation.getArgument(0)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.translateColors());
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void skipsExternalCallsForAnEmptyCatalog() {
        when(filaments.findAll()).thenReturn(List.of());
        when(shop.findAll()).thenReturn(List.of());
        assertEquals(0, service.translateColors().translatedColors());
        verifyNoInteractions(translations);
    }

    private FilamentVariant filament(String italian) {
        FilamentMaterialType material = new FilamentMaterialType();
        material.setMaterialCode("PLA");
        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(material);
        variant.setColorName("Black");
        variant.setColorHex("#aabbcc");
        variant.setColorLabelIt(italian);
        variant.setColorLabelEn("old English");
        variant.setIsActive(false);
        return variant;
    }

    private AdminTranslateLocalizedTextResponse response(AdminTranslateLocalizedTextRequest request) {
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        request.getFields().forEach((key, field) -> {
            String source = field.getValues().get("it");
            fields.put(key, Map.of("en", "en:" + source, "de", "de:" + source, "fr", "fr:" + source));
        });
        AdminTranslateLocalizedTextResponse response = new AdminTranslateLocalizedTextResponse();
        response.setFields(fields);
        return response;
    }
}

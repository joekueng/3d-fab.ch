package com.printcalculator.service.quote;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.service.MaterialPrintCompatibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class QuoteSessionSettingsServiceTest {
    private final QuoteSessionSettingsService service = new QuoteSessionSettingsService(
            mock(com.printcalculator.repository.PrinterMachineRepository.class),
            mock(com.printcalculator.repository.FilamentMaterialTypeRepository.class),
            mock(com.printcalculator.repository.FilamentVariantRepository.class),
            mock(com.printcalculator.service.NozzleLayerHeightPolicyService.class),
            mock(com.printcalculator.service.OrcaProfileResolver.class),
            mock(com.printcalculator.service.ProfileManager.class)
    );
    private final MaterialPrintCompatibilityService compatibility = new MaterialPrintCompatibilityService();

    @ParameterizedTest
    @CsvSource({
            "true,draft,0.24,15,grid", "false,draft,0.28,15,grid",
            "true,standard,0.20,15,grid", "false,standard,0.20,15,grid",
            "true,extra_fine,0.16,20,gyroid", "false,extra_fine,0.12,20,gyroid",
            "true,high_definition,0.16,20,gyroid", "true,high,0.16,20,gyroid"
    })
    void basicPresetsUseTheSelectedVariantMaterial(boolean technical, String quality, double expectedLayer,
                                                  double infill, String pattern) {
        FilamentVariant variant = variant(technical);
        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("BASIC");
        // The resolved variant is authoritative, including for older clients with ordinary presets.
        settings.setMaterial("PLA");
        settings.setQuality(quality);
        settings.setLayerHeight(0.28);

        service.applyPrintSettings(settings, variant);

        assertEquals(expectedLayer, settings.getLayerHeight());
        assertEquals(infill, settings.getInfillDensity());
        assertEquals(pattern, settings.getInfillPattern());
        assertEquals(0.4, settings.getNozzleDiameter());
        assertDoesNotThrow(() -> compatibility.validate(variant,
                BigDecimal.valueOf(settings.getNozzleDiameter()), BigDecimal.valueOf(settings.getLayerHeight())));
    }

    @Test
    void advancedSettingsStillRejectAnIncompatibleTechnicalLayer() {
        FilamentVariant variant = variant(true);
        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("ADVANCED");
        settings.setQuality("draft");
        settings.setLayerHeight(0.28);

        service.applyPrintSettings(settings, variant);

        assertEquals(0.28, settings.getLayerHeight());
        assertThrows(ResponseStatusException.class, () -> compatibility.validate(variant,
                BigDecimal.valueOf(settings.getNozzleDiameter()), BigDecimal.valueOf(settings.getLayerHeight())));
    }

    private FilamentVariant variant(boolean technical) {
        FilamentMaterialType material = new FilamentMaterialType();
        material.setMaterialCode(technical ? "PA12-CF" : "PLA");
        material.setIsTechnical(technical);
        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(material);
        return variant;
    }
}

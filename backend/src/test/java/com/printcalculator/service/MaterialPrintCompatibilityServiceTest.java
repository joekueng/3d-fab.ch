package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialPrintCompatibilityServiceTest {
    private final MaterialPrintCompatibilityService service = new MaterialPrintCompatibilityService();

    @Test
    void rejectsBlockedNozzlesForTechnicalMaterials() {
        FilamentVariant variant = variant(true);

        ResponseStatusException smallNozzle = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(variant, new BigDecimal("0.2"), new BigDecimal("0.12"))
        );
        ResponseStatusException largeNozzle = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(variant, new BigDecimal("0.80"), new BigDecimal("0.20"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, smallNozzle.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, largeNozzle.getStatusCode());
    }

    @Test
    void rejectsEveryLayerOutsideTheThreeTechnicalPresets() {
        FilamentVariant variant = variant(true);

        for (String layer : new String[]{"0.08", "0.12", "0.159", "0.18", "0.241", "0.28", "0.30"}) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.validate(variant, new BigDecimal("0.4"), new BigDecimal(layer))
            );
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
    }

    @Test
    void acceptsBoundaryAndDoesNotRestrictStandardMaterials() {
        for (String layer : new String[]{"0.16", "0.200", "0.240"}) {
            assertDoesNotThrow(() -> service.validate(
                    variant(true), new BigDecimal("0.4"), new BigDecimal(layer)
            ));
        }
        assertDoesNotThrow(() -> service.validate(
                variant(false),
                new BigDecimal("0.2"),
                new BigDecimal("0.08")
        ));
    }

    @Test
    void rejectsSmallNozzleForPlaCfEvenWhenNotMarkedTechnical() {
        FilamentVariant carbon = variant(false);
        carbon.getFilamentMaterialType().setMaterialCode(" pla-cf ");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.validate(carbon, new BigDecimal("0.20"), new BigDecimal("0.12")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertDoesNotThrow(() -> service.validate(carbon, new BigDecimal("0.4"), new BigDecimal("0.12")));
        assertDoesNotThrow(() -> service.validate(carbon, new BigDecimal("0.8"), new BigDecimal("0.28")));
    }

    private FilamentVariant variant(boolean technical) {
        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setIsTechnical(technical);
        FilamentVariant variant = new FilamentVariant();
        variant.setFilamentMaterialType(materialType);
        return variant;
    }

    @Test
    void tpu85aUsesOnlyTheVerifiedNozzleIndependentlyOfAdminClassification() {
        for (boolean technical : new boolean[]{true, false}) {
            var variant = variant(technical);
            variant.getFilamentMaterialType().setMaterialCode("TPU 85A");
            for (String nozzle : new String[]{"0.2", "0.4"}) {
                assertThrows(ResponseStatusException.class,
                        () -> service.validate(variant, new BigDecimal(nozzle), new BigDecimal("0.24")));
            }
            assertDoesNotThrow(() -> service.validate(variant, new BigDecimal("0.6"), new BigDecimal("0.24")));
            assertDoesNotThrow(() -> service.validate(variant, new BigDecimal("0.8"), new BigDecimal("0.24")));
        }
    }
}

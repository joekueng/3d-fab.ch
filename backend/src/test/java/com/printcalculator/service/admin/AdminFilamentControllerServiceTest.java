package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminFilamentMaterialTypeDto;
import com.printcalculator.dto.AdminFilamentVariantDto;
import com.printcalculator.dto.AdminUpsertFilamentMaterialTypeRequest;
import com.printcalculator.dto.AdminUpsertFilamentVariantRequest;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFilamentControllerServiceTest {

    @Mock
    private FilamentMaterialTypeRepository materialRepo;
    @Mock
    private FilamentVariantRepository variantRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private OrderItemRepository orderItemRepo;

    @InjectMocks
    private AdminFilamentControllerService service;

    @Test
    void createMaterial_withDuplicateCode_shouldReturnBadRequest() {
        AdminUpsertFilamentMaterialTypeRequest payload = new AdminUpsertFilamentMaterialTypeRequest();
        payload.setMaterialCode("pla");

        FilamentMaterialType existing = new FilamentMaterialType();
        existing.setId(1L);
        existing.setMaterialCode("PLA");
        when(materialRepo.findByMaterialCode("PLA")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createMaterial(payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(materialRepo, never()).save(any(FilamentMaterialType.class));
    }

    @Test
    void createVariant_withInvalidColorHex_shouldReturnBadRequest() {
        FilamentMaterialType material = new FilamentMaterialType();
        material.setId(10L);
        material.setMaterialCode("PLA");
        when(materialRepo.findById(10L)).thenReturn(Optional.of(material));
        when(variantRepo.findByFilamentMaterialTypeAndVariantDisplayName(material, "Sunset Orange"))
                .thenReturn(Optional.empty());

        AdminUpsertFilamentVariantRequest payload = baseVariantPayload();
        payload.setColorHex("#12");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createVariant(payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(variantRepo, never()).save(any(FilamentVariant.class));
    }

    @Test
    void createVariant_withValidPayload_shouldNormalizeDerivedFields() {
        FilamentMaterialType material = new FilamentMaterialType();
        material.setId(10L);
        material.setMaterialCode("PLA");
        when(materialRepo.findById(10L)).thenReturn(Optional.of(material));
        when(variantRepo.findByFilamentMaterialTypeAndVariantDisplayName(material, "Sunset Orange"))
                .thenReturn(Optional.empty());
        when(variantRepo.save(any(FilamentVariant.class))).thenAnswer(invocation -> {
            FilamentVariant variant = invocation.getArgument(0);
            variant.setId(42L);
            return variant;
        });

        AdminUpsertFilamentVariantRequest payload = baseVariantPayload();
        payload.setFinishType("matte");
        payload.setIsMatte(false);
        payload.setBrand("  Prusa  ");
        payload.setIsActive(null);

        AdminFilamentVariantDto dto = service.createVariant(payload);

        ArgumentCaptor<FilamentVariant> captor = ArgumentCaptor.forClass(FilamentVariant.class);
        verify(variantRepo).save(captor.capture());
        FilamentVariant saved = captor.getValue();

        assertEquals(42L, dto.getId());
        assertEquals("MATTE", saved.getFinishType());
        assertTrue(saved.getIsMatte());
        assertEquals("Prusa", saved.getBrand());
        assertTrue(saved.getIsActive());
    }

    @Test
    void deleteVariant_whenInUse_shouldReturnConflict() {
        Long variantId = 11L;
        FilamentVariant variant = new FilamentVariant();
        variant.setId(variantId);

        when(variantRepo.findById(variantId)).thenReturn(Optional.of(variant));
        when(quoteLineItemRepo.existsByFilamentVariant_Id(variantId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteVariant(variantId)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(variantRepo, never()).delete(any(FilamentVariant.class));
    }

    @Test
    void getMaterials_shouldReturnAlphabeticalByCode() {
        FilamentMaterialType abs = new FilamentMaterialType();
        abs.setId(2L);
        abs.setMaterialCode("ABS");

        FilamentMaterialType pla = new FilamentMaterialType();
        pla.setId(1L);
        pla.setMaterialCode("PLA");

        when(materialRepo.findAll()).thenReturn(List.of(pla, abs));

        List<AdminFilamentMaterialTypeDto> result = service.getMaterials();

        assertEquals(2, result.size());
        assertEquals("ABS", result.get(0).getMaterialCode());
        assertEquals("PLA", result.get(1).getMaterialCode());
    }

    private AdminUpsertFilamentVariantRequest baseVariantPayload() {
        AdminUpsertFilamentVariantRequest payload = new AdminUpsertFilamentVariantRequest();
        payload.setMaterialTypeId(10L);
        payload.setVariantDisplayName("Sunset Orange");
        payload.setColorName("Orange");
        payload.setColorHex("#FF8800");
        payload.setFinishType("GLOSSY");
        payload.setIsMatte(false);
        payload.setIsSpecial(false);
        payload.setCostChfPerKg(new BigDecimal("29.90"));
        payload.setStockSpools(new BigDecimal("2.000"));
        payload.setSpoolNetKg(new BigDecimal("1.000"));
        payload.setIsActive(true);
        return payload;
    }
}

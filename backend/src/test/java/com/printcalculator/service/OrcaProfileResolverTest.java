package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.MaterialOrcaProfileMap;
import com.printcalculator.entity.PrinterMachineProfile;
import com.printcalculator.repository.FilamentVariantOrcaOverrideRepository;
import com.printcalculator.repository.MaterialOrcaProfileMapRepository;
import com.printcalculator.repository.PrinterMachineProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrcaProfileResolverTest {
    private final MaterialOrcaProfileMapRepository maps = mock(MaterialOrcaProfileMapRepository.class);
    private final OrcaProfileResolver resolver = new OrcaProfileResolver(
            mock(PrinterMachineProfileRepository.class), maps, mock(FilamentVariantOrcaOverrideRepository.class));

    @ParameterizedTest
    @CsvSource({"PA12-CF,true,true", "PA12 CF,true,true", "pa12_cf,true,true",
            "PET-CF,true,true", "PET CF,true,true", "PET-CF,false,false",
            "PA12-CF,false,false", "PA6-CF,true,false", "PAHT-CF,true,false", "PLA,true,false"})
    void enablesAsaOnlyForPa12CfWithSupports(String material, boolean supports, boolean expected) {
        var profiles = new OrcaProfileResolver.ResolvedProfiles("machine", "model", null);
        assertEquals(expected ? Optional.of("Generic ASA") : Optional.empty(),
                resolver.resolveAsaSupportProfile(profiles, variant(material), supports));
        verifyNoInteractions(maps);
    }

    @Test
    void prefersThePrintersActiveAsaMappingWithoutUsingAnotherMaterial() {
        var machine = new PrinterMachineProfile();
        var profiles = new OrcaProfileResolver.ResolvedProfiles("machine", "model", machine);
        MaterialOrcaProfileMap asa = mapping("ASA", "Configured ASA");
        when(maps.findByPrinterMachineProfileAndIsActiveTrue(machine))
                .thenReturn(List.of(mapping("PLA", "Configured PLA"), asa));
        assertEquals(Optional.of("Configured ASA"), resolver.resolveAsaSupportProfile(profiles, variant("PA12-CF"), true));

        when(maps.findByPrinterMachineProfileAndIsActiveTrue(machine))
                .thenReturn(List.of(mapping("PLA", "Configured PLA")));
        assertEquals(Optional.of("Generic ASA"), resolver.resolveAsaSupportProfile(profiles, variant("PA12-CF"), true));
    }

    @Test
    void usesTheX2dAsaPresetForEachSupportedNozzle() {
        for (String nozzle : List.of("0.4", "0.6")) {
            var profiles = new OrcaProfileResolver.ResolvedProfiles("Bambu Lab X2D " + nozzle + " nozzle", "model", null);
            assertEquals(Optional.of("Generic ASA @BBL X2D" + (nozzle.equals("0.4") ? " 0.4 nozzle" : "")),
                    resolver.resolveAsaSupportProfile(profiles, variant("PET-CF"), true));
        }
    }

    private FilamentVariant variant(String materialCode) {
        var material = new FilamentMaterialType();
        material.setMaterialCode(materialCode);
        var variant = new FilamentVariant();
        variant.setFilamentMaterialType(material);
        return variant;
    }

    @ParameterizedTest
    @CsvSource({"PC-ABS,BambuLab P2S,0.4,Polymaker PC-ABS @BBL P2S",
            "TPU 85A,BambuLab P2S,0.6,Bambu TPU 85A @BBL P2S",
            "TPU 85A,BambuLab P2S,0.8,Bambu TPU 85A @BBL P2S 0.8 nozzle"})
    void specialStockMaterialsNeverFallBackToGenericPla(String material, String printer, String nozzle, String expected) {
        var machine = new com.printcalculator.entity.PrinterMachine();
        machine.setPrinterDisplayName(printer);
        assertEquals(expected, resolver.resolve(machine, new java.math.BigDecimal(nozzle), variant(material)).filamentProfileName());
        machine.setPrinterDisplayName("BambuLab A1");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> resolver.resolve(machine, new java.math.BigDecimal(nozzle), variant(material)));
    }

    private MaterialOrcaProfileMap mapping(String material, String profile) {
        var mapping = new MaterialOrcaProfileMap();
        mapping.setFilamentMaterialType(variant(material).getFilamentMaterialType());
        mapping.setOrcaFilamentProfileName(profile);
        return mapping;
    }
}

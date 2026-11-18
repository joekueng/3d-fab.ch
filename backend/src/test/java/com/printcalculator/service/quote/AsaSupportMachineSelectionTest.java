package com.printcalculator.service.quote;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.PrinterMachineProfile;
import com.printcalculator.service.OrcaProfileResolver;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsaSupportMachineSelectionTest {
    private final OrcaProfileResolver resolver = mock(OrcaProfileResolver.class);
    private final QuoteSessionSettingsService service = spy(new QuoteSessionSettingsService(
            mock(com.printcalculator.repository.PrinterMachineRepository.class),
            mock(com.printcalculator.repository.FilamentMaterialTypeRepository.class),
            mock(com.printcalculator.repository.FilamentVariantRepository.class),
            mock(com.printcalculator.service.NozzleLayerHeightPolicyService.class), resolver,
            mock(com.printcalculator.service.ProfileManager.class)));
    private final BigDecimal nozzle = new BigDecimal("0.4");
    private final BigDecimal layer = new BigDecimal("0.24");

    @Test
    void prefersX2dAndKeepsP2sAsTheFallbackWithoutSelectingA1() {
        var p2s = machine("P2S");
        var x2d = machine("X2D");
        var a1 = machine("A1");
        doReturn(List.of(p2s, a1, x2d)).when(service)
                .resolvePrinterMachineCandidates(null, nozzle, layer, "draft");
        assertEquals(List.of(x2d, p2s), service.resolveAsaSupportMachineCandidates(null, nozzle, layer, "draft"));
    }

    @Test
    void allowsP2sWhenX2dIsUnavailableAndHonorsAnExplicitPrinter() {
        var p2s = machine("P2S");
        doReturn(List.of(p2s)).when(service).resolvePrinterMachineCandidates(7L, nozzle, layer, "draft");
        assertEquals(List.of(p2s), service.resolveAsaSupportMachineCandidates(7L, nozzle, layer, "draft"));
    }

    @Test
    void rejectsWhenNeitherEnclosedPrinterHasACompatibleProfile() {
        var a1 = machine("A1");
        doReturn(List.of(a1)).when(service).resolvePrinterMachineCandidates(null, nozzle, layer, "draft");
        assertThrows(ResponseStatusException.class,
                () -> service.resolveAsaSupportMachineCandidates(null, nozzle, layer, "draft"));
    }

    @Test
    void anExplicitX2dStillAllowsTheRequestedP2sFallback() {
        var x2d = machine("X2D");
        var p2s = machine("P2S");
        doReturn(List.of(x2d)).when(service).resolvePrinterMachineCandidates(9L, nozzle, layer, "draft");
        doReturn(List.of(p2s, x2d)).when(service).resolvePrinterMachineCandidates(null, nozzle, layer, "draft");
        assertEquals(List.of(x2d, p2s), service.resolveAsaSupportMachineCandidates(9L, nozzle, layer, "draft"));
    }

    private PrinterMachine machine(String model) {
        var machine = new PrinterMachine();
        var profile = new PrinterMachineProfile();
        profile.setOrcaMachineProfileName("Bambu Lab " + model + " 0.4 nozzle");
        when(resolver.resolveMachineProfile(machine, nozzle)).thenReturn(Optional.of(profile));
        return machine;
    }
}

package com.printcalculator;

import com.printcalculator.controller.QuoteSessionController;
import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.StorageService;
import com.printcalculator.service.StlService;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.model.StlBounds;
import com.printcalculator.model.StlShiftResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.ArgumentCaptor;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

@WebMvcTest(QuoteSessionController.class)
public class ManualSessionPersistenceTest {

    @Autowired
    private QuoteSessionController controller;

    @MockitoBean
    private QuoteSessionRepository sessionRepo;

    @MockitoBean
    private QuoteLineItemRepository lineItemRepo; // Mock this too

    @MockitoBean
    private SlicerService slicerService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private StlService stlService;
    
    @MockitoBean
    private QuoteCalculator quoteCalculator;
    
    @MockitoBean
    private PrinterMachineRepository machineRepo;
    
    @MockitoBean
    private com.printcalculator.repository.PricingPolicyRepository pricingRepo; // Add this if needed by controller

    @Test
    public void testSettingsPersistence() throws Exception {
        // Prepare
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setMaterialCode("pla_basic"); // Initial state
        
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepo.save(any(QuoteSession.class))).thenAnswer(i -> i.getArguments()[0]);
        when(lineItemRepo.save(any(QuoteLineItem.class))).thenAnswer(i -> i.getArguments()[0]);

        // 2. Add Item with Custom Settings
        PrintSettingsDto settings = new PrintSettingsDto();
        settings.setComplexityMode("ADVANCED");
        settings.setMaterial("petg_basic");
        settings.setLayerHeight(0.12);
        settings.setInfillDensity(50.0);
        settings.setInfillPattern("gyroid");
        settings.setSupportsEnabled(true);
        settings.setNozzleDiameter(0.6);
        settings.setNotes("Test Notes");
        
        MockMultipartFile file = new MockMultipartFile("file", "test.stl", "application/octet-stream", "dummy content".getBytes());
        
        // Mock dependencies
        when(machineRepo.findFirstByIsActiveTrue()).thenReturn(Optional.of(new PrinterMachine(){{
            setPrinterDisplayName("TestPrinter");
            setSlicerMachineProfile("TestProfile");
            setBuildVolumeXMm(256);
            setBuildVolumeYMm(256);
            setBuildVolumeZMm(256);
        }}));
        when(slicerService.slice(any(), any(), any(), any(), any(), any())).thenReturn(new PrintStats(100, "1m", 10.0, 100));
        when(quoteCalculator.calculate(any(), any(), any())).thenReturn(
            new QuoteResult(10.0, "CHF", new PrintStats(100, "1m", 10.0, 100), 0.0)
        );
        when(stlService.readBounds(any())).thenReturn(new StlBounds(0, 0, 0, 10, 10, 10));
        when(stlService.shiftToFitIfNeeded(any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new StlShiftResult(null, 0, 0, 0, false));
        when(storageService.loadAsResource(any())).thenReturn(new org.springframework.core.io.ByteArrayResource("dummy".getBytes()){
            @Override
            public File getFile() { return new File("dummy"); }
        });

        controller.addItemToExistingSession(sessionId, settings, file);
        
        // 3. Verify Session Updated via Save Call capture
        ArgumentCaptor<QuoteSession> captor = ArgumentCaptor.forClass(QuoteSession.class);
        verify(sessionRepo).save(captor.capture());
        
        QuoteSession updatedSession = captor.getValue();
        
        assertEquals("petg_basic", updatedSession.getMaterialCode());
        assertEquals(0, BigDecimal.valueOf(0.12).compareTo(updatedSession.getLayerHeightMm()));
        assertEquals(50, updatedSession.getInfillPercent());
        assertEquals("gyroid", updatedSession.getInfillPattern());
        assertTrue(updatedSession.getSupportsEnabled());
        assertEquals(0, BigDecimal.valueOf(0.6).compareTo(updatedSession.getNozzleDiameterMm()));
        assertEquals("Test Notes", updatedSession.getNotes());
        
        System.out.println("Verification Passed: Settings were persisted to Session.");
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }
    }
}

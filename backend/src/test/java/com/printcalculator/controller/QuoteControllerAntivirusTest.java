package com.printcalculator.controller;

import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.NozzleLayerHeightPolicyService;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuoteControllerAntivirusTest {
    @Mock private SlicerService slicer;
    @Mock private QuoteCalculator calculator;
    @Mock private PrinterMachineRepository machines;
    @Mock private NozzleLayerHeightPolicyService nozzlePolicy;
    @Mock private QuoteRateLimitService rateLimit;

    @ParameterizedTest
    @ValueSource(strings = {"/api/quote", "/calculate/stl"})
    void disabledScannerRejectsUploadBeforeSlicing(String endpoint) throws Exception {
        var controller = new QuoteController(slicer, calculator, machines,
                new ClamAVService("localhost", 3310, false), nozzlePolicy, rateLimit);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var file = new MockMultipartFile("file", "model.stl", "model/stl",
                "solid model".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart(endpoint).file(file)).andExpect(status().isServiceUnavailable());

        verifyNoInteractions(slicer, calculator, machines);
    }
}

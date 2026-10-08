package com.printcalculator.controller;

import com.printcalculator.dto.PrintSettingsDto;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.information.OrderInformationService;
import com.printcalculator.service.quote.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class QuoteSessionRateLimitControllerTest {
    @Mock private QuoteSessionRepository sessions;
    @Mock private QuoteLineItemRepository items;
    @Mock private QuoteCalculator calculator;
    @Mock private PricingPolicyRepository policies;
    @Mock private QuoteSessionTotalsService totals;
    @Mock private QuoteSessionItemService itemService;
    @Mock private QuoteStorageService storage;
    @Mock private QuoteSessionResponseAssembler assembler;
    @Mock private QuoteSessionExpiryPolicy expiry;
    @Mock private QuoteSessionCalculationService calculation;
    @Mock private OrderInformationService information;
    @Spy private QuoteRateLimitService limits = new QuoteRateLimitService(15, 60, false, 15, 75);
    @InjectMocks private QuoteSessionController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @CsvSource({"1,15", "2,15", "10,7", "15,5"})
    void batchBudgetIsCheckedBeforeExistingQuoteIsCleared(int fileCount, int allowedCalculations) throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        when(calculation.prepareForRecalculation(sessionId)).thenReturn(Optional.of(session));
        String payload = "{\"reuseSessionId\":\"" + sessionId + "\",\"itemCount\":" + fileCount + "}";
        for (int i = 0; i < allowedCalculations; i++) {
            mvc.perform(post("/api/quote-sessions").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.calculationId").isNotEmpty());
        }
        mvc.perform(post("/api/quote-sessions").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        verify(calculation, times(allowedCalculations)).prepareForRecalculation(sessionId);
        verifyNoInteractions(itemService, storage, sessions);
    }

    @Test
    void invalidFileCountDoesNotClearAnExistingQuote() throws Exception {
        for (int count : new int[] {0, -1, 76}) {
            mvc.perform(post("/api/quote-sessions").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemCount\":" + count + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(calculation, sessions, itemService);
    }

    @Test
    void sessionBoundPermitIsConsumedBeforeSlicingAndCannotBeReplayed() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID permit = limits.reserveCalculation(request, 1);
        limits.bindCalculation(permit, sessionId);
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(itemService.addItemToSession(eq(session), any(), any(PrintSettingsDto.class)))
                .thenReturn(new QuoteLineItem());
        var file = new MockMultipartFile("file", "part.stl", "model/stl", new byte[] {1});
        var settings = new MockMultipartFile("settings", "settings.json", "application/json", "{}".getBytes());
        mvc.perform(multipart("/api/quote-sessions/{id}/line-items", UUID.randomUUID())
                .file(file).file(settings).header("X-Quote-Calculation", permit))
                .andExpect(status().isForbidden());
        verifyNoInteractions(itemService);
        mvc.perform(multipart("/api/quote-sessions/{id}/line-items", sessionId)
                .file(file).file(settings).header("X-Quote-Calculation", permit))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/quote-sessions/{id}/line-items", sessionId)
                .file(file).file(settings).header("X-Quote-Calculation", permit))
                .andExpect(status().isForbidden());
        verify(itemService).addItemToSession(eq(session), any(), any(PrintSettingsDto.class));
    }
}

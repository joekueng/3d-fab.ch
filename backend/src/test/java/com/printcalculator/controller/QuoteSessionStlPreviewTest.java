package com.printcalculator.controller;

import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.quote.QuoteStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuoteSessionStlPreviewTest {
    @TempDir Path tempDir;

    @Test
    void servesConvertedStlFor3mfAndRetainsSessionAndFormatChecks() throws Exception {
        var items = mock(QuoteLineItemRepository.class);
        var storage = spy(new QuoteStorageService());
        var sessions = mock(com.printcalculator.repository.QuoteSessionRepository.class);
        var controller = new QuoteSessionController(sessions, items, null, null, null, null, storage,
                null, null, null, null, null, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var session = new QuoteSession();
        session.setId(UUID.randomUUID());
        var item = new QuoteLineItem();
        item.setId(UUID.randomUUID());
        item.setQuoteSession(session);
        item.setOriginalFilename("assembly.3mf");
        item.setStoredPath("original.3mf");
        item.setPricingBreakdown(Map.of("convertedStoredPath", "preview.stl"));
        Path preview = Files.writeString(tempDir.resolve("preview.stl"), "solid converted");
        doReturn(preview).when(storage).resolveStoredQuotePath("preview.stl", session.getId());
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        String route = "/api/quote-sessions/{session}/line-items/{item}/stl-preview";

        mvc.perform(get(route, session.getId(), item.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("model/stl"))
                .andExpect(content().string("solid converted"));
        mvc.perform(get(route, UUID.randomUUID(), item.getId()))
                .andExpect(status().isNotFound());

        var invoice = new QuoteSession(); invoice.setId(UUID.randomUUID()); invoice.setStatus("CAD_ACTIVE");
        invoice.setCalculatorSession(session);
        when(sessions.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        mvc.perform(get(route, invoice.getId(), item.getId()))
                .andExpect(status().isOk()).andExpect(content().string("solid converted"));
        invoice.setCalculatorDetached(true);
        mvc.perform(get(route, invoice.getId(), item.getId())).andExpect(status().isNotFound());
        invoice.setCalculatorDetached(false);
        mvc.perform(get(route, invoice.getId(), item.getId())).andExpect(status().isOk());
        item.setPricingBreakdown(Map.of());
        doReturn(tempDir.resolve("original.3mf")).when(storage)
                .resolveStoredQuotePath("original.3mf", session.getId());
        Files.writeString(tempDir.resolve("original.3mf"), "original project");
        mvc.perform(get(route, session.getId(), item.getId()))
                .andExpect(status().isNotFound());
    }
}

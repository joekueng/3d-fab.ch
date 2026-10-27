package com.printcalculator.controller;

import com.printcalculator.controller.admin.AdminOperationsController;
import com.printcalculator.dto.AdminCadInvoiceCreateRequest;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.admin.AdminOperationsControllerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadInvoiceLinkControllerTest {
    @Test
    void publicSummaryFollowsTheSavedCalculatorSelectionAndRestoresOriginalRows() throws Exception {
        var sessions = mock(QuoteSessionRepository.class);
        var items = mock(com.printcalculator.repository.QuoteLineItemRepository.class);
        var totals = mock(com.printcalculator.service.QuoteSessionTotalsService.class);
        var attachments = mock(com.printcalculator.service.quote.QuoteSessionAttachmentService.class);
        var assembler = new com.printcalculator.service.quote.QuoteSessionResponseAssembler(
                mock(com.printcalculator.service.quote.QuoteStorageService.class));
        var controller = new QuoteSessionController(sessions, items, null, null, totals, null, null,
                assembler, null, null, null, attachments, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var invoice = new QuoteSession(); invoice.setId(UUID.randomUUID()); invoice.setStatus("CAD_ACTIVE");
        invoice.setSetupCostChf(java.math.BigDecimal.ONE);
        var source = new QuoteSession(); source.setId(UUID.randomUUID()); source.setSetupCostChf(java.math.BigDecimal.TEN);
        var ownItem = new com.printcalculator.entity.QuoteLineItem(); ownItem.setDisplayName("original.stl");
        var otherItem = new com.printcalculator.entity.QuoteLineItem(); otherItem.setDisplayName("replacement.stl");
        when(sessions.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(items.findByQuoteSessionIdOrderByCreatedAtAsc(invoice.getId())).thenReturn(java.util.List.of(ownItem));
        when(items.findByQuoteSessionIdOrderByCreatedAtAsc(source.getId())).thenReturn(java.util.List.of(otherItem));
        var zero = java.math.BigDecimal.ZERO;
        when(totals.compute(eq(invoice), any())).thenReturn(
                new com.printcalculator.service.QuoteSessionTotalsService.QuoteSessionTotals(
                        zero, zero, zero, zero, zero, zero, zero, zero, zero, zero));
        invoice.setCalculatorSession(source);
        mvc.perform(get("/api/quote-sessions/{id}", invoice.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(invoice.getId().toString()))
                .andExpect(jsonPath("$.session.setupCostChf").value(10))
                .andExpect(jsonPath("$.items[0].displayName").value("replacement.stl"));
        invoice.setCalculatorDetached(true);
        mvc.perform(get("/api/quote-sessions/{id}", invoice.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.setupCostChf").value(0)).andExpect(jsonPath("$.items").isEmpty());
        invoice.setCalculatorDetached(false); invoice.setCalculatorSession(null);
        mvc.perform(get("/api/quote-sessions/{id}", invoice.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.setupCostChf").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("original.stl"));
    }

    @Test
    void nullableCalculatorAssociationIsIndependentOfTheCheckoutPathIdentity() throws Exception {
        var service = mock(AdminOperationsControllerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(service)).build();
        UUID invoice = UUID.randomUUID(); UUID calculator = UUID.randomUUID();
        for (String selection : new String[]{"null", "\"" + calculator + "\""}) {
            mvc.perform(put("/api/admin/cad-invoices/{id}", invoice).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cadHours\":1,\"calculatorSessionId\":" + selection + "}"))
                    .andExpect(status().isOk());
        }
        var captor = org.mockito.ArgumentCaptor.forClass(AdminCadInvoiceCreateRequest.class);
        verify(service, times(2)).createOrUpdateCadInvoice(captor.capture());
        var calls = captor.getAllValues();
        calls.forEach(payload -> { assertEquals(invoice, payload.getSessionId());
            org.junit.jupiter.api.Assertions.assertTrue(payload.hasCalculatorSessionId()); });
        org.junit.jupiter.api.Assertions.assertNull(calls.get(0).getCalculatorSessionId());
        assertEquals(calculator, calls.get(1).getCalculatorSessionId());
    }

    @Test
    void editUsesThePathIdWhenBodyIdIsMissingOrDifferent() throws Exception {
        var service = mock(AdminOperationsControllerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(service)).build();
        UUID id = UUID.randomUUID();
        for (String body : new String[]{"{\"cadHours\":1}", "{\"cadHours\":1,\"sessionId\":\"" + UUID.randomUUID() + "\"}"}) {
            mvc.perform(put("/api/admin/cad-invoices/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        }
        var captor = org.mockito.ArgumentCaptor.forClass(AdminCadInvoiceCreateRequest.class);
        verify(service, times(2)).createOrUpdateCadInvoice(captor.capture());
        captor.getAllValues().forEach(payload -> assertEquals(id, payload.getSessionId()));
        mvc.perform(post("/api/admin/cad-invoices/{id}/delete", id)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).deleteCadInvoice(any(), any());
    }

    @Test
    void deletedLinkOnlyReturnsRedirectOrGoneAndNeverInvoiceData() throws Exception {
        var sessions = mock(QuoteSessionRepository.class);
        var controller = new QuoteSessionController(sessions, null, null, null, null, null,
                null, null, null, null, null, null, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        UUID id = UUID.randomUUID();
        var session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_DELETED");
        session.setNotes("Should never be returned");
        session.setDeletedRedirectPath("/contact");
        when(sessions.findById(id)).thenReturn(Optional.of(session));
        mvc.perform(get("/api/quote-sessions/{id}", id))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.redirectPath").value("/contact"))
                .andExpect(jsonPath("$.session").doesNotExist());
        session.setDeletedRedirectPath(null);
        mvc.perform(get("/api/quote-sessions/{id}", id))
                .andExpect(status().isGone()).andExpect(header().string("Cache-Control", "no-store"));
    }
}

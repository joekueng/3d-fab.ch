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

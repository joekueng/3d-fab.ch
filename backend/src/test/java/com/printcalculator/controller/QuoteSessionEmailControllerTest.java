package com.printcalculator.controller;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.service.quote.QuoteSessionEmailService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuoteSessionEmailControllerTest {
    private final QuoteSessionEmailService service = mock(QuoteSessionEmailService.class);
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new QuoteSessionEmailController(service)).build();
    }
    @Test void rejectsInvalidRecipientAndLanguageBeforeSending() throws Exception {
        mvc.perform(post("/api/quote-sessions/" + id + "/email").contentType("application/json")
                .content("""
                    {"email":"not-an-email","language":"invalid","mode":"easy","information":{"id":"%s","token":"key"}}
                    """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsMissingCredential() throws Exception {
        mvc.perform(post("/api/quote-sessions/" + id + "/email").contentType("application/json")
                .content("""
                    {"email":"customer@example.test","language":"it","mode":"easy"}
                    """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void acceptsValidRequestAndDoesNotCacheTheResponse() throws Exception {
        mvc.perform(post("/api/quote-sessions/" + id + "/email").contentType("application/json")
                .content("""
                    {"email":"customer@example.test","language":"en","mode":"advanced","information":{"id":"%s","token":"key"}}
                    """.formatted(UUID.randomUUID())))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).send(eq(id), any());
    }
    @Test void resumeLoadsTheStoredCredentialWithoutAKeyInTheRequest() throws Exception {
        UUID draft = UUID.randomUUID();
        when(service.resume(id)).thenReturn(new InformationDto.Credential(draft, "key"));
        mvc.perform(post("/api/quote-sessions/" + id + "/resume").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(draft.toString()));
    }
}

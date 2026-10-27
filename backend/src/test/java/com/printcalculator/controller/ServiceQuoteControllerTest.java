package com.printcalculator.controller;

import com.printcalculator.service.LegalConsentRequestService;
import com.printcalculator.service.servicequote.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceQuoteControllerTest {
    @Test void rejectsInvalidNestedIdentityAndMissingConfirmationsBeforeCallingTheService() throws Exception {
        var quotes=mock(ServiceQuoteService.class);var docs=mock(ServiceQuoteDocumentService.class);
        var controller=new ServiceQuoteController(quotes,docs,mock(LegalConsentRequestService.class),mock(ServiceQuoteRateLimiter.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        String path="/api/service-quotes/"+UUID.randomUUID()+"/revisions/"+UUID.randomUUID()+"/accept";
        String payload="""
            {"customer":{"email":"invalid","customerType":"BUSINESS","companyName":"","contactPerson":"","addressLine1":"Example 1","zip":"6900","city":"Lugano","countryCode":"CH"},
             "requestId":"%s","downloadToken":"%s","contentVersion":"version","acceptQuote":false,"acceptTerms":false,"acceptPrivacy":false}
            """.formatted(UUID.randomUUID(),"a".repeat(64));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        verifyNoInteractions(quotes,docs);
    }
    @Test void pdfIsOnlyReturnedAfterAuthorizationAndIsNeverCached() throws Exception {
        var quotes=mock(ServiceQuoteService.class);var docs=mock(ServiceQuoteDocumentService.class);
        var controller=new ServiceQuoteController(quotes,docs,mock(LegalConsentRequestService.class),mock(ServiceQuoteRateLimiter.class));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();var id=UUID.randomUUID();
        when(docs.download(id)).thenReturn("%PDF-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/service-quote-acceptances/{id}/pdf",id).header("X-Quote-Download-Token","private-token"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Referrer-Policy","no-referrer"));
        var order=inOrder(quotes,docs);order.verify(quotes).authorizeDownload(id,"private-token");order.verify(docs).download(id);
    }
}

package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContactRequestLocalizationServiceTest {

    private ContactRequestLocalizationService service;

    @BeforeEach
    void setUp() {
        service = new ContactRequestLocalizationService();
    }

    @Test
    void normalizeLanguage_shouldMapKnownPrefixes() {
        assertEquals("de", service.normalizeLanguage("de-CH"));
        assertEquals("en", service.normalizeLanguage("EN"));
        assertEquals("fr", service.normalizeLanguage("fr_CA"));
        assertEquals("it", service.normalizeLanguage(""));
    }

    @Test
    void resolveRecipientName_shouldUsePriorityAndFallback() {
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setName("Mario Rossi");
        assertEquals("Mario Rossi", service.resolveRecipientName(request, "it"));

        request.setName(" ");
        request.setContactPerson("Laura Bianchi");
        assertEquals("Laura Bianchi", service.resolveRecipientName(request, "it"));

        request.setContactPerson(" ");
        request.setCompanyName("3D Fab SA");
        assertEquals("3D Fab SA", service.resolveRecipientName(request, "it"));

        request.setCompanyName(" ");
        assertEquals("customer", service.resolveRecipientName(request, "en"));
    }

    @Test
    void applyCustomerContactRequestTexts_shouldPopulateLocalizedLabels() {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("recipientName", "Mario");
        UUID requestId = UUID.randomUUID();

        String subject = service.applyCustomerContactRequestTexts(templateData, "fr", requestId);

        assertEquals("Nous avons recu votre demande de contact #" + requestId + " - 3D-Fab", subject);
        assertEquals("Date", templateData.get("labelDate"));
        assertEquals("Bonjour Mario,", templateData.get("greetingText"));
    }

    @Test
    void localizeRequestType_andCustomerType_shouldReturnExpectedValues() {
        assertEquals("Custom part request", service.localizeRequestType("print_service", "en"));
        assertEquals("Azienda", service.localizeCustomerType("business", "it"));
        assertEquals("-", service.localizeCustomerType(null, "de"));
    }

    @Test
    void localeForLanguage_shouldReturnExpectedLocale() {
        assertEquals(Locale.GERMAN, service.localeForLanguage("de"));
        assertEquals(Locale.ITALIAN, service.localeForLanguage("unknown"));
    }
}

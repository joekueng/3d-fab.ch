package com.printcalculator.service;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.LegalConsent;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.LegalConsentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalConsentServiceTest {
    @Mock LegalConsentRepository repository;
    @InjectMocks LegalConsentService service;

    @ParameterizedTest
    @CsvSource({"de-CH,de", " FR ,fr", "en-US,en", "it,it", "es,it", "'',it"})
    void snapshotsAcceptanceWithSupportedLanguageAndServerVersions(String language, String expected) {
        Order order = new Order();
        order.setCustomerEmail("buyer@example.test");
        OffsetDateTime before = OffsetDateTime.now();

        service.recordOrder(order, true, true, language, "203.0.113.42");
        order.setCustomerEmail("changed@example.test");

        var captured = ArgumentCaptor.forClass(LegalConsent.class);
        verify(repository).save(captured.capture());
        LegalConsent consent = captured.getValue();
        assertSame(order, consent.getOrder());
        assertNull(consent.getContactRequest());
        assertTrue(consent.isTermsAccepted());
        assertTrue(consent.isPrivacyAccepted());
        assertFalse(consent.getAcceptedAt().isBefore(before));
        assertFalse(consent.getAcceptedAt().isAfter(OffsetDateTime.now()));
        assertEquals("buyer@example.test", consent.getEmail());
        assertEquals("203.0.113.42", consent.getClientIp());
        assertEquals(expected, consent.getLanguage());
        assertEquals("v1.1-2026-09-16", consent.getTermsVersion());
        assertEquals("2026-09-16", consent.getPrivacyVersion());
    }

    @Test
    void recordsContactRequestWithFallbackLanguage() {
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setEmail("contact@example.test");
        service.recordContactRequest(request, true, true, null, "203.0.113.42");

        var captured = ArgumentCaptor.forClass(LegalConsent.class);
        verify(repository).save(captured.capture());
        LegalConsent consent = captured.getValue();
        assertSame(request, consent.getContactRequest());
        assertNull(consent.getOrder());
        assertEquals("contact@example.test", consent.getEmail());
        assertEquals("it", consent.getLanguage());
        assertEquals("203.0.113.42", consent.getClientIp());
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void refusesToRecordMissingAcceptance(boolean terms, boolean privacy) {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordOrder(new Order(), terms, privacy, "it", "203.0.113.42"));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordContactRequest(new CustomQuoteRequest(), terms, privacy, "it", "203.0.113.42"));
        verifyNoInteractions(repository);
    }

    @Test
    void requiresExactlyOneSubmission() {
        assertThrows(IllegalArgumentException.class, () -> new LegalConsent(null, null, true, true, "v1", "v1", "it", "203.0.113.42"));
        assertThrows(IllegalArgumentException.class, () -> new LegalConsent(new Order(), new CustomQuoteRequest(), true, true, "v1", "v1", "it", "203.0.113.42"));
    }

    @Test
    void recordsNormalizedIpv6() {
        service.recordOrder(new Order(), true, true, "it", "2001:db8::42");
        var captured = ArgumentCaptor.forClass(LegalConsent.class);
        verify(repository).save(captured.capture());
        assertEquals("2001:db8:0:0:0:0:0:42", captured.getValue().getClientIp());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"unknown", "not-an-ip"})
    void keepsUnavailableIpNull(String ip) {
        service.recordOrder(new Order(), true, true, "it", ip);
        var captured = ArgumentCaptor.forClass(LegalConsent.class);
        verify(repository).save(captured.capture());
        assertNull(captured.getValue().getClientIp());
    }
}

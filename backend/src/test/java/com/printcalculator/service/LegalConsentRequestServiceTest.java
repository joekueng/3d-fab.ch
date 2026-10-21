package com.printcalculator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegalConsentRequestServiceTest {
    @ParameterizedTest
    @CsvSource({
            "false,'',203.0.113.42",
            "true,'',203.0.113.42",
            "false,10.0.0.0/8,10.0.0.2",
            "true,10.0.0.0/8,203.0.113.42",
            "true,10.0.0.0/8,192.168.0.2"
    })
    void ignoresSpoofedHeadersWithoutAnEnabledTrustedPeer(boolean trust, String networks, String remoteIp) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteIp);
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("Forwarded", "for=198.51.100.99");
        request.addHeader("X-Real-IP", "198.51.100.99");

        assertEquals(remoteIp, new LegalConsentRequestService(trust, networks).resolveClientIp(request));
    }

    @ParameterizedTest
    @CsvSource({
            "X-Forwarded-For,'198.51.100.99, 203.0.113.42, 10.0.0.3',203.0.113.42",
            "Forwarded,for=203.0.113.42;proto=https,203.0.113.42",
            "X-Real-IP,203.0.113.42,203.0.113.42",
            "Forwarded,'for=\"[2001:db8::42]:443\"',2001:db8:0:0:0:0:0:42"
    })
    void resolvesForwardedIpFromTrustedProxy(String header, String value, String expected) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader(header, value);

        assertEquals(expected, new LegalConsentRequestService(true, "10.0.0.0/8").resolveClientIp(request));
    }

    @Test
    void normalizesDirectIpv6() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::42");
        assertEquals("2001:db8:0:0:0:0:0:42", new LegalConsentRequestService(false, "").resolveClientIp(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.test", "unknown", "999.999.999.999"})
    void invalidProxyHeaderFallsBackToConnectingIp(String value) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", value);
        assertEquals("10.0.0.2", new LegalConsentRequestService(true, "10.0.0.0/8").resolveClientIp(request));
    }

    @Test
    void unavailableRemoteIpIsNotInventedFromUntrustedHeaders() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        assertNull(new LegalConsentRequestService(true, "10.0.0.0/8").resolveClientIp(request));
    }
}

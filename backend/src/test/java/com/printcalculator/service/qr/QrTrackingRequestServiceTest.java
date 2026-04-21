package com.printcalculator.service.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrTrackingRequestServiceTest {

    @Test
    void resolveClientIp_shouldUseRemoteAddress_WhenProxyHeadersAreNotTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", false, "");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertEquals("10.0.0.5", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldUseFirstForwardedForValue_WhenRequestComesFromTrustedProxy() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 172.20.0.2");
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("203.0.113.10", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldFallbackToRealIp_WhenRequestComesFromTrustedProxy() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("1.1.1.1", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldIgnoreForwardedHeaders_WhenRemoteAddressIsNotTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("198.51.100.24");

        assertEquals("198.51.100.24", service.resolveClientIp(request));
    }
}

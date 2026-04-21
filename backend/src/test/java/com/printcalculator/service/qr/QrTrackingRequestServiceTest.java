package com.printcalculator.service.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrTrackingRequestServiceTest {

    @Test
    void resolveClientIp_shouldUseRemoteAddress_WhenProxyHeadersAreNotTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertEquals("10.0.0.5", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldUseFirstPublicIpFromForwardedFor_WhenProxyHeadersAreTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.5, 8.8.8.8, 172.20.0.2");
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("8.8.8.8", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldFallbackToRealIp_WhenForwardedForIsMissing() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("1.1.1.1", service.resolveClientIp(request));
    }
}

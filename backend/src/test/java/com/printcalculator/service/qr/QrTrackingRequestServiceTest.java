package com.printcalculator.service.qr;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrTrackingRequestServiceTest {

    private final QrTrackingRequestService service = new QrTrackingRequestService(
            "secret-value-for-tests",
            false,
            "",
            false
    );

    @Test
    void resolveClientIp_shouldUseRemoteAddress_WhenProxyHeadersAreNotTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", false, "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertEquals("10.0.0.5", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldUseFirstForwardedForValue_WhenRequestComesFromTrustedProxy() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 172.20.0.2");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("203.0.113.10", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldUseRightMostPublicUntrustedForwardedForValue_WhenHeaderContainsSpoofedPrefix() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99, 8.8.8.8");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("8.8.8.8", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldSupportStandardForwardedHeader_WhenRequestComesFromTrustedProxy() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Forwarded")).thenReturn("for=8.8.4.4;proto=https, for=172.20.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("8.8.4.4", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldPreferForwardedForOverForwarded_WhenBothHeadersExist() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Forwarded")).thenReturn("for=1.1.1.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("8.8.8.8", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldFallbackToRealIp_WhenRequestComesFromTrustedProxy() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("1.1.1.1", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldIgnoreForwardedHeaders_WhenRemoteAddressIsNotTrusted() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "172.20.0.0/16", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(request.getHeader("X-Real-IP")).thenReturn("1.1.1.1");
        when(request.getRemoteAddr()).thenReturn("198.51.100.24");

        assertEquals("198.51.100.24", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIp_shouldUseForwardedForFallback_WhenRemoteAddressIsPrivateButProxyCidrDoesNotMatch() {
        QrTrackingRequestService service = new QrTrackingRequestService("secret", true, "10.0.0.0/8", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 172.20.0.2");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.20.0.2");

        assertEquals("203.0.113.10", service.resolveClientIp(request));
    }

    @Test
    void isSuspectedBot_shouldFlagKnownPreviewBots() {
        assertTrue(service.isSuspectedBot("LinkedInBot/1.0 (compatible; Mozilla/5.0; +http://www.linkedin.com)"));
        assertTrue(service.isSuspectedBot("Mozilla/5.0 (compatible; bingpreview/1.0b)"));
        assertTrue(service.isSuspectedBot("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"));
    }

    @Test
    void isSuspectedBot_shouldIgnoreBrowserLikePreviewTokens() {
        assertFalse(service.isSuspectedBot(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [LinkedInApp]/9.31.6712"
        ));
        assertFalse(service.isSuspectedBot(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36 PreviewShell/1.0"
        ));
    }
}

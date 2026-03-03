package com.printcalculator.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminLoginThrottleServiceTest {

    private final AdminLoginThrottleService service = new AdminLoginThrottleService(false);

    @Test
    void registerFailure_ShouldDoubleDelay() {
        assertEquals(2L, service.registerFailure("127.0.0.1"));
        assertEquals(4L, service.registerFailure("127.0.0.1"));
        assertEquals(8L, service.registerFailure("127.0.0.1"));
    }

    @Test
    void resolveClientKey_ShouldUseRemoteAddress_WhenProxyHeadersAreNotTrusted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.11");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertEquals("10.0.0.5", service.resolveClientKey(request));
    }

    @Test
    void resolveClientKey_ShouldUseForwardedFor_WhenProxyHeadersAreTrusted() {
        AdminLoginThrottleService trustedService = new AdminLoginThrottleService(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.5");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        assertEquals("203.0.113.10", trustedService.resolveClientKey(request));
    }
}

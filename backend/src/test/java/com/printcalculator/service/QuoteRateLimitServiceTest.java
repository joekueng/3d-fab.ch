package com.printcalculator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class QuoteRateLimitServiceTest {

    private static final int MAX_REQUESTS = 3;

    private QuoteRateLimitService newService(int maxRequests, boolean trustProxyHeaders) {
        return new QuoteRateLimitService(maxRequests, 60, trustProxyHeaders);
    }

    @Test
    void checkSlicingAllowed_withinBudget_shouldNotThrow() {
        QuoteRateLimitService service = newService(MAX_REQUESTS, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertDoesNotThrow(() -> service.checkSlicingAllowed(request));
        }
    }

    @Test
    void checkSlicingAllowed_whenBudgetExhausted_shouldThrow429() {
        QuoteRateLimitService service = newService(MAX_REQUESTS, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.11");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            service.checkSlicingAllowed(request);
        }

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.checkSlicingAllowed(request)
        );
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertEquals("60", ex.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void checkSlicingAllowed_shouldTrackClientsIndependently() {
        QuoteRateLimitService service = newService(MAX_REQUESTS, false);
        MockHttpServletRequest clientA = new MockHttpServletRequest();
        clientA.setRemoteAddr("203.0.113.20");
        MockHttpServletRequest clientB = new MockHttpServletRequest();
        clientB.setRemoteAddr("203.0.113.21");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            service.checkSlicingAllowed(clientA);
        }
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(clientA));
        assertDoesNotThrow(() -> service.checkSlicingAllowed(clientB));
    }

    @Test
    void checkSlicingAllowed_withTrustedProxyHeader_shouldUseForwardedFor() {
        QuoteRateLimitService service = newService(MAX_REQUESTS, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            service.checkSlicingAllowed(request);
        }
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(request));
    }

    @Test
    void checkSlicingAllowed_withUntrustedProxyHeader_shouldIgnoreForwardedFor() {
        QuoteRateLimitService service = newService(MAX_REQUESTS, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.9");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        for (int i = 0; i < MAX_REQUESTS; i++) {
            service.checkSlicingAllowed(request);
        }
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(request));
    }
}

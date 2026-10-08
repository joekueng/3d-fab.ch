package com.printcalculator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class QuoteRateLimitServiceTest {

    private static final int MAX_REQUESTS = 3;

    private QuoteRateLimitService newService(int maxRequests, boolean trustProxyHeaders) {
        return new QuoteRateLimitService(maxRequests, 60, trustProxyHeaders, 15, 75);
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
    @Test
    void fiveBatchesOfFifteenFilesUseFiveCalculationsNotSeventyFiveRequests() {
        QuoteRateLimitService service = newService(15, false);
        MockHttpServletRequest request = client("203.0.113.10");
        for (int batch = 0; batch < 5; batch++) {
            UUID sessionId = UUID.randomUUID();
            UUID permit = service.reserveCalculation(request, 15);
            service.bindCalculation(permit, sessionId);
            for (int file = 0; file < 15; file++) {
                assertDoesNotThrow(() -> service.checkSlicingAllowed(request, sessionId, permit));
            }
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                    () -> service.checkSlicingAllowed(request, sessionId, permit)).getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 1)).getStatusCode());
    }

    @Test
    void invalidCalculationLimitFallsBackToFifteenClicks() {
        QuoteRateLimitService service = new QuoteRateLimitService(15, 60, false, 0, 75);
        MockHttpServletRequest request = client("203.0.113.10");
        for (int calculation = 0; calculation < 15; calculation++) {
            assertDoesNotThrow(() -> service.reserveCalculation(request, 1));
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 1)).getStatusCode());
    }

    @Test
    void fileBudgetRejectsWholeBatchWithoutChargingIt() {
        QuoteRateLimitService service = newService(15, false);
        MockHttpServletRequest request = client("203.0.113.10");
        service.reserveCalculation(request, 60);
        assertThrows(ResponseStatusException.class, () -> service.reserveCalculation(request, 20));
        assertDoesNotThrow(() -> service.reserveCalculation(request, 15));
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(request));
        assertDoesNotThrow(() -> service.reserveCalculation(client("203.0.113.11"), 15));
    }

    @Test
    void reservationsCannotBeUsedByAnotherIpOrSession() {
        QuoteRateLimitService service = newService(15, false);
        MockHttpServletRequest request = client("203.0.113.10");
        UUID sessionId = UUID.randomUUID();
        UUID permit = service.reserveCalculation(request, 1);
        service.bindCalculation(permit, sessionId);
        assertThrows(ResponseStatusException.class,
                () -> service.checkSlicingAllowed(client("203.0.113.11"), sessionId, permit));
        assertThrows(ResponseStatusException.class,
                () -> service.checkSlicingAllowed(request, UUID.randomUUID(), permit));
        assertDoesNotThrow(() -> service.checkSlicingAllowed(request, sessionId, permit));
    }

    @Test
    void slidingWindowExpiresAcceptedWorkEvenWhenRejectedRequestsContinue() {
        AtomicLong now = new AtomicLong(0);
        QuoteRateLimitService service = timedService(2, 75, now);
        MockHttpServletRequest request = client("203.0.113.10");
        service.reserveCalculation(request, 10);
        now.set(10_000);
        service.reserveCalculation(request, 10);
        now.set(20_000);
        assertEquals("40", assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 10)).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        now.set(59_000);
        assertEquals("1", assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 10)).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        now.set(60_000);
        assertDoesNotThrow(() -> service.reserveCalculation(request, 10));
        assertThrows(ResponseStatusException.class, () -> service.reserveCalculation(request, 10));
        now.set(70_000);
        assertDoesNotThrow(() -> service.reserveCalculation(request, 10));
    }

    @Test
    void retryAfterWaitsUntilEnoughFileBudgetExpires() {
        AtomicLong now = new AtomicLong(0);
        QuoteRateLimitService service = timedService(5, 75, now);
        MockHttpServletRequest request = client("203.0.113.10");
        service.reserveCalculation(request, 20);
        now.set(10_000);
        service.reserveCalculation(request, 50);
        assertEquals("60", assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 40)).getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void legacyRequestsAlsoUseASlidingWindow() {
        AtomicLong now = new AtomicLong(0);
        QuoteRateLimitService service = timedService(5, 75, now);
        MockHttpServletRequest request = client("203.0.113.10");
        for (int i = 0; i < 15; i++) service.checkSlicingAllowed(request);
        now.set(59_000);
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(request));
        now.set(60_000);
        assertDoesNotThrow(() -> service.checkSlicingAllowed(request));
    }

    @Test
    void invalidCountsAndExpiredPermitsCannotStartWork() {
        AtomicLong now = new AtomicLong(0);
        QuoteRateLimitService service = timedService(5, 75, now);
        MockHttpServletRequest request = client("203.0.113.10");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.reserveCalculation(request, 0)).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.reserveCalculation(request, 76));
        UUID sessionId = UUID.randomUUID();
        UUID permit = service.reserveCalculation(request, 1);
        service.bindCalculation(permit, sessionId);
        now.set(15 * 60_000L);
        assertThrows(ResponseStatusException.class, () -> service.checkSlicingAllowed(request, sessionId, permit));
    }

    @Test
    void concurrentBatchAdmissionsCannotOverspendTheBudget() throws Exception {
        QuoteRateLimitService service = newService(15, false);
        MockHttpServletRequest request = client("203.0.113.10");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(10)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int attempt = 0; attempt < 10; attempt++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.reserveCalculation(request, 15);
                        return true;
                    } catch (ResponseStatusException exception) {
                        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(2, TimeUnit.SECONDS)) accepted++;
            assertEquals(5, accepted);
        }
    }

    @Test
    void concurrentUploadsCannotReplayTheLastReservedFile() throws Exception {
        QuoteRateLimitService service = newService(15, false);
        MockHttpServletRequest request = client("203.0.113.10");
        UUID sessionId = UUID.randomUUID();
        UUID permit = service.reserveCalculation(request, 1);
        service.bindCalculation(permit, sessionId);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int attempt = 0; attempt < 2; attempt++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.checkSlicingAllowed(request, sessionId, permit);
                        return true;
                    } catch (ResponseStatusException exception) {
                        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(2, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        }
    }

    private QuoteRateLimitService timedService(int calculations, int files, AtomicLong now) {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(invocation -> now.get());
        return new QuoteRateLimitService(15, 60, false, calculations, files, clock);
    }

    private MockHttpServletRequest client(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

}

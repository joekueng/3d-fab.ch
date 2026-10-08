package com.printcalculator.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SlicingCapacityServiceTest {
    @Test
    void fullCapacityRejectsAndClosingLeaseReleasesExactlyOneSlot() {
        SlicingCapacityService service = new SlicingCapacityService(1, 0, 0);
        var lease = service.acquire();
        ResponseStatusException error = assertThrows(ResponseStatusException.class, service::acquire);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("5", error.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        lease.close();
        lease.close();
        try (var next = service.acquire()) {
            assertThrows(ResponseStatusException.class, service::acquire);
        }
    }

    @Test
    void queuedWorkStartsWhenRunningJobFinishes() throws Exception {
        SlicingCapacityService service = new SlicingCapacityService(1, 1, 5);
        var first = service.acquire();
        try (var executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch started = new CountDownLatch(1);
            var future = executor.submit(() -> {
                started.countDown();
                try (var lease = service.acquire()) { return true; }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            first.close();
            assertTrue(future.get(2, TimeUnit.SECONDS));
        } finally {
            first.close();
        }
    }

    @Test
    void queueTimeoutDoesNotLeakAdmissionSlots() {
        SlicingCapacityService service = new SlicingCapacityService(1, 1, 0);
        try (var running = service.acquire()) {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThrows(ResponseStatusException.class, service::acquire);
            }
        }
        assertDoesNotThrow(() -> service.acquire().close());
    }

    @Test
    void errorsReleaseCapacityAndInterruptedWaitsPreserveInterruption() {
        SlicingCapacityService service = new SlicingCapacityService(1, 1, 5);
        assertThrows(IllegalStateException.class, () -> {
            try (var lease = service.acquire()) { throw new IllegalStateException("failed job"); }
        });
        try (var running = service.acquire()) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(ResponseStatusException.class, service::acquire);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
        assertDoesNotThrow(() -> service.acquire().close());
    }
}

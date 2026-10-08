package com.printcalculator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounds active quote jobs and waiting requests across public and admin quotes. */
@Service
public class SlicingCapacityService {
    private final Semaphore active;
    private final Semaphore admitted;
    private final long queueWaitSeconds;

    public SlicingCapacityService(
            @Value("${quote.slicing.max-concurrent:2}") int maxConcurrent,
            @Value("${quote.slicing.max-queued:20}") int maxQueued,
            @Value("${quote.slicing.queue-wait-seconds:60}") long queueWaitSeconds) {
        if (maxConcurrent < 1 || maxQueued < 0 || queueWaitSeconds < 0) {
            throw new IllegalArgumentException("Invalid quote slicing capacity configuration");
        }
        this.active = new Semaphore(maxConcurrent, true);
        this.admitted = new Semaphore(Math.addExact(maxConcurrent, maxQueued));
        this.queueWaitSeconds = queueWaitSeconds;
    }

    public Lease acquire() {
        if (!admitted.tryAcquire()) throw busy();
        boolean acquired = false;
        try {
            acquired = active.tryAcquire(queueWaitSeconds, TimeUnit.SECONDS);
            if (!acquired) throw busy();
            return new Lease();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw busy();
        } finally {
            if (!acquired) admitted.release();
        }
    }

    private ResponseStatusException busy() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many calculations are running. Please try again shortly.") {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, "5");
                return headers;
            }
        };
    }

    public final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.release();
                admitted.release();
            }
        }
    }
}

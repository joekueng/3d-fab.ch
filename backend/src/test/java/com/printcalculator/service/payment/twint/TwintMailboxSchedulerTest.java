package com.printcalculator.service.payment.twint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwintMailboxSchedulerTest {
    private final TwintMailboxReader reader = mock(TwintMailboxReader.class);
    private final TwintProperties config = new TwintProperties();
    private final MutableClock clock = new MutableClock();
    private TwintMailboxScheduler scheduler;

    @BeforeEach void setup() {
        config.setEnabled(true);
        scheduler = new TwintMailboxScheduler(reader, config, clock);
        scheduler.initialize();
    }

    @Test void periodicModeReadsOnStartupThenEveryThreeHours() throws Exception {
        scheduler.tick();
        clock.advance(Duration.ofSeconds(5)); scheduler.tick();
        clock.advance(Duration.ofHours(3).minusSeconds(6)); scheduler.tick();
        verify(reader, times(1)).poll();
        clock.advance(Duration.ofSeconds(1)); scheduler.tick();
        verify(reader, times(2)).poll();
    }

    @Test void activeOrdersShareOneRateAndNewOrdersCannotAccelerateIt() throws Exception {
        when(reader.hasActivePaymentWindow()).thenReturn(true);
        scheduler.tick();
        for (int i = 0; i < 100; i++) scheduler.tick();
        clock.advance(Duration.ofMillis(4999)); scheduler.tick();
        verify(reader, times(1)).poll();
        clock.advance(Duration.ofMillis(1)); scheduler.tick();
        verify(reader, times(2)).poll();
    }

    @Test void newWindowActivatesPollingAndLastWindowClosingReturnsToPeriodic() throws Exception {
        scheduler.tick();
        clock.advance(Duration.ofMinutes(30));
        when(reader.hasActivePaymentWindow()).thenReturn(true);
        scheduler.tick();
        verify(reader, times(2)).poll();
        // The repository becomes false on expiry, payment or cancellation of the last window.
        when(reader.hasActivePaymentWindow()).thenReturn(false);
        clock.advance(Duration.ofSeconds(5)); scheduler.tick();
        verify(reader, times(2)).poll();
        clock.advance(Duration.ofHours(3).minusSeconds(5)); scheduler.tick();
        verify(reader, times(3)).poll();
    }

    @Test void intervalsStartWhenReadingFinishesAndFailuresDoNotBusyRetry() throws Exception {
        doAnswer(call -> {
            clock.advance(Duration.ofSeconds(20));
            throw new IllegalStateException("fixture failure");
        }).when(reader).poll();
        scheduler.tick();
        clock.advance(Duration.ofHours(3).minusMillis(1)); scheduler.tick();
        verify(reader, times(1)).poll();
        clock.advance(Duration.ofMillis(1)); scheduler.tick();
        verify(reader, times(2)).poll();
    }

    @Test void activeFailureRetriesAtActiveInterval() throws Exception {
        when(reader.hasActivePaymentWindow()).thenReturn(true);
        doThrow(new IllegalStateException("fixture failure")).when(reader).poll();
        scheduler.tick();
        scheduler.tick();
        verify(reader, times(1)).poll();
        clock.advance(Duration.ofSeconds(5)); scheduler.tick();
        verify(reader, times(2)).poll();
    }

    @Test void restartChecksOnceAndReevaluatesPersistedWindows() throws Exception {
        scheduler.tick();
        when(reader.hasActivePaymentWindow()).thenReturn(true);
        var restarted = new TwintMailboxScheduler(reader, config, clock);
        restarted.initialize();
        restarted.tick();
        restarted.tick();
        verify(reader, times(2)).poll();
        clock.advance(Duration.ofSeconds(5)); restarted.tick();
        verify(reader, times(3)).poll();
    }

    @Test void disabledOrNotReadyNeverReadsMailbox() throws Exception {
        var notReady = new TwintMailboxScheduler(reader, config, clock);
        notReady.tick();
        config.setEnabled(false);
        scheduler.tick();
        verify(reader, never()).hasActivePaymentWindow();
        verify(reader, never()).poll();
    }

    @Test void simultaneousTicksNeverOverlapOrAddExtraReads() throws Exception {
        when(reader.hasActivePaymentWindow()).thenReturn(true);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(reader).poll();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(scheduler::tick);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var second = executor.submit(scheduler::tick);
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                verify(reader, times(1)).poll();
            } finally {
                release.countDown();
            }
        }
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-25T12:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

package com.printcalculator.service.payment.twint;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class TwintMailboxScheduler {
    private final TwintMailboxReader reader;
    private final TwintProperties config;
    private final Clock clock;
    private volatile boolean initialized;
    private Instant lastAttemptCompleted;
    private Boolean activeMode;

    @Autowired
    public TwintMailboxScheduler(TwintMailboxReader reader, TwintProperties config) {
        this(reader, config, Clock.systemUTC());
    }

    TwintMailboxScheduler(TwintMailboxReader reader, TwintProperties config, Clock clock) {
        this.reader = reader;
        this.config = config;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        reader.initialize();
        initialized = true;
    }

    // Only this dedicated, single-thread scheduler invokes poll. Order events never
    // enqueue extra reads. The lightweight database check also detects mode changes.
    @Scheduled(fixedDelayString = "${app.twint.inbox.poll-ms:5000}", scheduler = "twintMailboxTaskScheduler")
    public synchronized void tick() {
        if (!initialized || !config.isEnabled()) return;
        try {
            boolean active = reader.hasActivePaymentWindow();
            Duration interval = active ? Duration.ofMillis(config.getPollMs()) : config.getPeriodicInterval();
            if (activeMode == null || activeMode != active) {
                log.info("TWINT inbox mode={}; interval={}", active ? "ACTIVE" : "PERIODIC", interval);
                activeMode = active;
            }
            if (lastAttemptCompleted != null && clock.instant().isBefore(lastAttemptCompleted.plus(interval))) return;
            log.info("TWINT inbox check started: mode={}", active ? "ACTIVE" : "PERIODIC");
            try {
                reader.poll();
                log.info("TWINT inbox check completed");
            } finally {
                // Failed reads respect the same interval; their database transaction
                // rolls back, so the next attempt resumes from the committed cursor.
                lastAttemptCompleted = clock.instant();
            }
        } catch (Exception e) {
            log.warn("TWINT mailbox check interrupted ({}); retry at the current mode interval",
                    e.getClass().getSimpleName());
        }
    }
}

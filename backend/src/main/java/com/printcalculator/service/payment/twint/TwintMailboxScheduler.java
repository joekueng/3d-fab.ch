package com.printcalculator.service.payment.twint;

import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class TwintMailboxScheduler {
    private final TwintMailboxReader reader;
    private final TwintProperties config;
    private final TaskScheduler scheduler;
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile boolean initialized;

    public TwintMailboxScheduler(TwintMailboxReader reader, TwintProperties config,
            @Qualifier("twintMailboxTaskScheduler") TaskScheduler scheduler) {
        this.reader = reader;
        this.config = config;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        reader.initialize();
        initialized = true;
        tick();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = {OrderCreatedEvent.class, PaymentReportedEvent.class})
    public void wakeUp() {
        if (config.isEnabled() && queued.compareAndSet(false, true)) {
            scheduler.schedule(() -> { queued.set(false); tick(); }, Instant.now());
        }
    }

    @Scheduled(fixedDelayString = "${app.twint.inbox.poll-ms:5000}", scheduler = "twintMailboxTaskScheduler")
    public void tick() {
        if (!initialized || !config.isEnabled()) return;
        try { reader.poll(); }
        catch (Exception e) {
            log.warn("TWINT mailbox check interrupted ({}); cursor will resume on next active check", e.getClass().getSimpleName());
        }
    }
}

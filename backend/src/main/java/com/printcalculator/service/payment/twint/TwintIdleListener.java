package com.printcalculator.service.payment.twint;

import jakarta.annotation.PreDestroy;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.event.ConnectionAdapter;
import jakarta.mail.event.ConnectionEvent;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.IdleManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;

/** Owns the IMAP connection; all reconciliation remains in the transactional reader. */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.twint.inbox.idle-enabled", havingValue = "true", matchIfMissing = true)
public class TwintIdleListener {
    private final TwintMailboxReader reader;
    private final TwintProperties config;
    private final Semaphore wakeup = new Semaphore(0);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "twint-idle-reader"));
    private final ExecutorService selector = Executors.newFixedThreadPool(2,
            task -> new Thread(task, "twint-idle-selector"));
    private volatile boolean running;
    private volatile IMAPFolder folder;
    private volatile IdleManager manager;

    public TwintIdleListener(TwintMailboxReader reader, TwintProperties config) {
        this.reader = reader;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) return;
        reader.initialize();
        if (!config.isEnabled()) return;
        if (config.getIdleRenewal().compareTo(Duration.ofMinutes(1)) < 0
                || config.getIdleRenewal().compareTo(Duration.ofMinutes(25)) > 0
                || config.getIdleFallbackInterval().compareTo(Duration.ofSeconds(10)) < 0) {
            throw new IllegalStateException("TWINT IDLE renewal must be 1..25 minutes and fallback at least 10 seconds");
        }
        running = true;
        worker.submit(this::run);
    }

    private void run() {
        int failures = 0;
        while (running) {
            long started = System.nanoTime();
            try {
                listen();
                failures = 0;
            } catch (Exception error) {
                if (!running) break;
                if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(1)) failures = 0;
                long delay = Math.min(300, 5L << Math.min(failures++, 6));
                log.warn("TWINT IDLE disconnected ({}); reconnect in {} seconds",
                        error.getClass().getSimpleName(), delay);
                // Mail callbacks must not shorten the retry backoff.
                try { TimeUnit.SECONDS.sleep(delay); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private void listen() throws Exception {
        Session session = createSession();
        try (IMAPStore store = (IMAPStore) session.getStore("imaps")) {
            store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
            if (!running) return;
            IMAPFolder current = (IMAPFolder) store.getFolder(config.getFolder());
            folder = current;
            try {
                current.open(Folder.READ_ONLY);
                current.addMessageCountListener(new MessageCountAdapter() {
                    @Override public void messagesAdded(MessageCountEvent event) { signal(); }
                    @Override public void messagesRemoved(MessageCountEvent event) { signal(); }
                });
                current.addConnectionListener(new ConnectionAdapter() {
                    @Override public void closed(ConnectionEvent event) { signal(); }
                    @Override public void disconnected(ConnectionEvent event) { signal(); }
                });
                boolean idle = store.hasCapability("IDLE");
                if (idle) manager = createManager(session);
                log.info("TWINT inbox connected: mode={}", idle ? "IDLE" : "FALLBACK");
                if (!idle) log.warn("TWINT server does not advertise IDLE; using {} recovery reads",
                        config.getIdleFallbackInterval());
                long renewAt = System.nanoTime() + config.getIdleRenewal().toNanos();
                while (running && System.nanoTime() < renewAt) {
                    wakeup.drainPermits();
                    drain(current);
                    if (!running) return;
                    if (!idle) {
                        wakeup.tryAcquire(config.getIdleFallbackInterval().toMillis(), TimeUnit.MILLISECONDS);
                    } else {
                        // Re-arm after every folder operation. Notifications during the read
                        // remain queued in the semaphore, closing the read/watch race.
                        manager.watch(current);
                        wakeup.tryAcquire(Math.max(1, renewAt - System.nanoTime()), TimeUnit.NANOSECONDS);
                    }
                }
            } finally {
                if (manager != null) { manager.stop(); manager = null; }
                try { if (current.isOpen()) current.close(false); }
                finally { folder = null; }
            }
        }
    }

    void drain(Folder current) throws Exception {
        while (running && reader.readBatch(current)) {
            // Each call commits separately; drain a backlog before returning to IDLE.
        }
    }

    Session createSession() { return Session.getInstance(connectionProperties()); }

    IdleManager createManager(Session session) throws java.io.IOException {
        return new IdleManager(session, selector);
    }

    private void signal() {
        // A burst only requires one more UID scan.
        if (wakeup.availablePermits() == 0) wakeup.release();
    }

    static Properties connectionProperties() {
        var properties = new Properties();
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imaps.connectiontimeout", "5000");
        properties.setProperty("mail.imaps.timeout", "5000");
        properties.setProperty("mail.imaps.writetimeout", "5000");
        properties.setProperty("mail.imaps.peek", "true");
        properties.setProperty("mail.imaps.usesocketchannels", "true");
        return properties;
    }

    @PreDestroy
    public void stop() {
        running = false;
        signal();
        IdleManager activeManager = manager;
        if (activeManager != null) activeManager.stop();
        IMAPFolder activeFolder = folder;
        if (activeFolder != null) {
            try { activeFolder.forceClose(); }
            catch (Exception ignored) { /* Already disconnected. */ }
        }
        worker.shutdownNow();
        selector.shutdownNow();
    }
}

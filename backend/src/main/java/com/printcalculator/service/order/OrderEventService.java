package com.printcalculator.service.order;

import com.printcalculator.event.OrderStatusChangedEvent;
import com.printcalculator.event.PaymentConfirmedEvent;
import com.printcalculator.event.PaymentReportedEvent;
import com.printcalculator.repository.OrderRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Public invalidations only; order data continues to use the existing GET contract. */
@Service
public class OrderEventService {
    private final OrderRepository orders;
    private final ConcurrentHashMap<SseEmitter, Subscription> subscriptions = new ConcurrentHashMap<>();

    public OrderEventService(OrderRepository orders) { this.orders = orders; }

    public synchronized SseEmitter subscribe(UUID orderId) {
        if (!orders.existsById(orderId)) throw new ResponseStatusException(NOT_FOUND);
        if (subscriptions.size() >= 1000 || subscriptions.values().stream()
                .filter(s -> s.orderId.equals(orderId)).count() >= 10) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Too many order streams");
        }
        var emitter = new SseEmitter(300_000L);
        var subscription = new Subscription(orderId);
        subscriptions.put(emitter, subscription);
        emitter.onCompletion(() -> subscriptions.remove(emitter));
        emitter.onTimeout(() -> { subscriptions.remove(emitter); emitter.complete(); });
        emitter.onError(error -> subscriptions.remove(emitter));
        return emitter;
    }

    @TransactionalEventListener
    public void confirmed(PaymentConfirmedEvent event) { changed(event.getOrder().getId()); }

    @TransactionalEventListener
    public void reported(PaymentReportedEvent event) { changed(event.getOrder().getId()); }

    @TransactionalEventListener
    public void statusChanged(OrderStatusChangedEvent event) { changed(event.orderId()); }

    private void changed(UUID orderId) {
        subscriptions.values().stream().filter(s -> s.orderId.equals(orderId))
                .forEach(s -> s.dirty.set(true));
    }

    // Network writes never run in payment transactions or on the mailbox/email workers.
    @Scheduled(fixedDelay = 250, scheduler = "orderEventsTaskScheduler")
    public void flush() {
        long now = System.nanoTime();
        subscriptions.forEach((emitter, subscription) -> {
            boolean changed = subscription.dirty.getAndSet(false);
            if (!changed && now - subscription.lastSent < 15_000_000_000L) return;
            try {
                emitter.send(SseEmitter.event().name(changed ? "order-changed" : "heartbeat")
                        .reconnectTime(5000).data("{}"));
                subscription.lastSent = now;
            } catch (IOException | IllegalStateException error) {
                subscriptions.remove(emitter);
                emitter.completeWithError(error);
            }
        });
    }

    @PreDestroy
    public void close() {
        subscriptions.forEach((emitter, subscription) -> emitter.complete());
        subscriptions.clear();
    }

    private static class Subscription {
        final UUID orderId;
        final AtomicBoolean dirty = new AtomicBoolean(true);
        volatile long lastSent;
        Subscription(UUID orderId) { this.orderId = orderId; }
    }
}

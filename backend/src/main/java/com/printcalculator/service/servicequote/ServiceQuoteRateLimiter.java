package com.printcalculator.service.servicequote;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/** Bounded, per-instance fixed window budgets, matching the existing link-email throttling. */
@Service
public class ServiceQuoteRateLimiter {
    private record Window(long endsAt, int count) {}
    private final Map<String,Window> windows = new HashMap<>();
    public synchronized void check(String key, int limit) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> e.getValue().endsAt() <= now);
        Window w = windows.get(key);
        if (windows.size() >= 10000 && w == null || w != null && w.count() >= limit)
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "QUOTE_RATE_LIMIT");
        windows.put(key, new Window(w == null ? now+60000 : w.endsAt(), w == null ? 1 : w.count()+1));
    }
}

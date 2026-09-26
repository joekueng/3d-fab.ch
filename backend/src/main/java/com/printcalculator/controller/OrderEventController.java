package com.printcalculator.controller;

import com.printcalculator.service.order.OrderEventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderEventController {
    private final OrderEventService events;
    public OrderEventController(OrderEventService events) { this.events = events; }

    @GetMapping(value = "/{orderId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable UUID orderId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("X-Accel-Buffering", "no").body(events.subscribe(orderId));
    }
}

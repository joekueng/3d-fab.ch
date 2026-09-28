package com.printcalculator.event;

import java.util.UUID;

public record OrderStatusChangedEvent(UUID orderId) {}

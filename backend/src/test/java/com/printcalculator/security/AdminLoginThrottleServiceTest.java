package com.printcalculator.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminLoginThrottleServiceTest {

    private final AdminLoginThrottleService service = new AdminLoginThrottleService();

    @Test
    void registerFailure_ShouldDoubleDelay() {
        assertEquals(2L, service.registerFailure("127.0.0.1"));
        assertEquals(4L, service.registerFailure("127.0.0.1"));
        assertEquals(8L, service.registerFailure("127.0.0.1"));
    }
}

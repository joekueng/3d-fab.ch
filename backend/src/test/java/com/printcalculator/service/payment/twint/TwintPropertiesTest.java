package com.printcalculator.service.payment.twint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class TwintPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TwintProperties.class)
    static class Config {}
    @Test void bindsDeploymentSettingsAndEmptyDisabledCutoff() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Config.class);
        runner.withPropertyValues("app.twint.inbox.initial-since=", "app.twint.inbox.enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertNull(context.getBean(TwintProperties.class).getInitialSince());
        });
        runner.withPropertyValues("app.twint.inbox.initial-since=2026-09-23T10:00:00+02:00",
                "app.twint.inbox.active-window=PT10M").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(OffsetDateTime.parse("2026-09-23T10:00:00+02:00"), context.getBean(TwintProperties.class).getInitialSince());
            assertEquals(Duration.ofMinutes(10), context.getBean(TwintProperties.class).getActiveWindow());
        });
    }
}

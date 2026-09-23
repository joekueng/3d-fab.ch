package com.printcalculator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PaymentSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler paymentEmailTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("payment-email-");
        return scheduler;
    }
    @Bean
    public ThreadPoolTaskScheduler twintMailboxTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("twint-mailbox-");
        return scheduler;
    }
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}

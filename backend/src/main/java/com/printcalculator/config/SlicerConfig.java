package com.printcalculator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "") 
// Hack: standard prefix is usually required. I'll use @Value in service or correct this.
// Better: make SlicerConfig class.
public class SlicerConfig {
    // Intentionally empty, will use @Value in service for simplicity 
    // or fix in next step.
}

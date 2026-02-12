package com.printcalculator.config;

import com.printcalculator.service.ClamAVService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.InputStream;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ClamAVService mockClamAVService() {
        return new ClamAVService("localhost", 3310) {
            @Override
            public boolean scan(InputStream inputStream) {
                return true; // Always clean for tests
            }
        };
    }
}

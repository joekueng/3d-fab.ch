package com.printcalculator.service.payment.twint;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.OffsetDateTime;

@Component
@ConfigurationProperties(prefix = "app.twint.inbox")
@Getter
@Setter
public class TwintProperties {
    private boolean enabled;
    private String host = "mail.infomaniak.com";
    private int port = 993;
    private String username = "info@3d-fab.ch";
    private String password;
    private String originalRecipient = "joekueng05@gmail.com";
    private String folder = "INBOX";
    private OffsetDateTime initialSince;
    private Duration activeWindow = Duration.ofMinutes(10);
    private long pollMs = 5000;
    private Duration periodicInterval = Duration.ofHours(3);
    private int batchSize = 50;

    public String mailboxKey() { return host + "/" + username + "/" + folder; }
}

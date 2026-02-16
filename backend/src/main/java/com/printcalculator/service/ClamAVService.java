package com.printcalculator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

@Service
public class ClamAVService {

    private static final Logger logger = LoggerFactory.getLogger(ClamAVService.class);

    private final ClamavClient clamavClient;
    private final boolean enabled;

    public ClamAVService(
            @Value("${clamav.host:clamav}") String host,
            @Value("${clamav.port:3310}") int port,
            @Value("${clamav.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
        if (!enabled) {
            logger.info("ClamAV is DISABLED");
            this.clamavClient = null;
            return;
        }
        logger.info("Initializing ClamAV client at {}:{}", host, port);
        ClamavClient client = null;
        try {
            client = new ClamavClient(host, port);
        } catch (Exception e) {
            logger.error("Failed to initialize ClamAV client: " + e.getMessage());
        }
        this.clamavClient = client;
    }

    public boolean scan(InputStream inputStream) {
        if (!enabled || clamavClient == null) {
            return true;
        }
        try {
            ScanResult result = clamavClient.scan(inputStream);
            if (result instanceof ScanResult.OK) {
                return true;
            } else if (result instanceof ScanResult.VirusFound) {
                Map<String, Collection<String>> viruses = ((ScanResult.VirusFound) result).getFoundViruses();
                logger.warn("VIRUS DETECTED: {}", viruses);
                return false;
            } else {
                logger.warn("Unknown scan result: {}. Allowing file (FAIL-OPEN)", result);
                return true;
            }
        } catch (Exception e) {
            logger.error("Error scanning file with ClamAV. Allowing file (FAIL-OPEN)", e);
            return true;
        }
    }
}

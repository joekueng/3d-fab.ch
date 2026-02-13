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

    public ClamAVService(
            @Value("${clamav.host:localhost}") String host,
            @Value("${clamav.port:3310}") int port
    ) {
        logger.info("Initializing ClamAV client at {}:{}", host, port);
        try {
            this.clamavClient = new ClamavClient(host, port);
        } catch (Exception e) {
            logger.error("Failed to initialize ClamAV client: " + e.getMessage());
            // We don't throw exception here to allow app to start even if ClamAV is down/unreachable
            // scan() method will handle null client or failure
            throw new RuntimeException("ClamAV initialization failed", e);
        }
    }

    public boolean scan(InputStream inputStream) {
        try {
            ScanResult result = clamavClient.scan(inputStream);
            if (result instanceof ScanResult.OK) {
                return true;
            } else if (result instanceof ScanResult.VirusFound) {
                Map<String, Collection<String>> viruses = ((ScanResult.VirusFound) result).getFoundViruses();
                logger.warn("VIRUS DETECTED: {}", viruses);
                return false;
            } else {
                logger.warn("Unknown scan result: {}", result);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error scanning file with ClamAV", e);
            // Fail safe? Or fail secure? 
            // Usually if scanner fails, we should probably reject to be safe, or allow with warning depending on policy.
            // For now, let's reject to be safe.
            return false;
        }
    }
}

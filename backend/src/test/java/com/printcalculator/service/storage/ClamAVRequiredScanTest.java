package com.printcalculator.service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class ClamAVRequiredScanTest {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "CLAMAV_LIVE_TEST", matches = "true")
    void liveScannerAcceptsCleanDataAndRejectsEicar() {
        var service = new ClamAVService("127.0.0.1", 3310, true);
        assertTrue(service.scanRequired(new ByteArrayInputStream("%PDF-1.7\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        assertThrows(com.printcalculator.exception.VirusDetectedException.class,
                () -> service.scanRequired(new ByteArrayInputStream(eicar.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
    }

    @Test void disabledScannerNeverApprovesPrivateAttachments() {
        var service = new ClamAVService("localhost", 3310, false);
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.scanRequired(new ByteArrayInputStream(new byte[]{1}))).getStatusCode().value());
    }
}

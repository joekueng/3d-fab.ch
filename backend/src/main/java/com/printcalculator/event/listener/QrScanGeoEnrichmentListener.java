package com.printcalculator.event.listener;

import com.printcalculator.event.QrScanRecordedEvent;
import com.printcalculator.service.qr.QrScanGeoEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QrScanGeoEnrichmentListener {

    private final QrScanGeoEnrichmentService qrScanGeoEnrichmentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(QrScanRecordedEvent event) {
        if (event.qrScanEventId() == null) {
            return;
        }
        try {
            qrScanGeoEnrichmentService.enrich(event.qrScanEventId(), event.clientIp());
        } catch (Exception ex) {
            log.warn("Unable to enrich QR scan {} with GeoLite2 data", event.qrScanEventId(), ex);
        }
    }
}

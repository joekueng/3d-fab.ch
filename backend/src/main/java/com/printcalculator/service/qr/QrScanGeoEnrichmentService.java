package com.printcalculator.service.qr;

import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrScanEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QrScanGeoEnrichmentService {
    private static final Logger logger = LoggerFactory.getLogger(QrScanGeoEnrichmentService.class);

    private final QrScanEventRepository qrScanEventRepository;
    private final GeoLite2CityService geoLite2CityService;
    private final boolean debugLogging;

    public QrScanGeoEnrichmentService(QrScanEventRepository qrScanEventRepository,
                                      GeoLite2CityService geoLite2CityService,
                                      @Value("${app.qr.debug-logging:false}") boolean debugLogging) {
        this.qrScanEventRepository = qrScanEventRepository;
        this.geoLite2CityService = geoLite2CityService;
        this.debugLogging = debugLogging;
    }

    @Transactional
    public void enrich(UUID qrScanEventId, String clientIp) {
        if (qrScanEventId == null) {
            return;
        }

        if (debugLogging) {
            logger.info("QR geo debug: starting enrichment. qrScanEventId={}, clientIp={}", qrScanEventId, clientIp);
        }

        geoLite2CityService.lookup(clientIp).ifPresentOrElse(
                location -> qrScanEventRepository.findById(qrScanEventId)
                        .ifPresentOrElse(
                                event -> applyLocation(event, location),
                                () -> {
                                    if (debugLogging) {
                                        logger.info("QR geo debug: event not found during enrichment. qrScanEventId={}", qrScanEventId);
                                    }
                                }),
                () -> {
                    if (debugLogging) {
                        logger.info("QR geo debug: no location resolved. qrScanEventId={}, clientIp={}", qrScanEventId, clientIp);
                    }
                }
        );
    }

    private void applyLocation(QrScanEvent event, GeoLite2CityService.GeoLocation location) {
        event.setCountryCode(location.countryCode());
        event.setCountryName(location.countryName());
        event.setCityName(location.cityName());
        qrScanEventRepository.save(event);
        if (debugLogging) {
            logger.info("QR geo debug: saved location. qrScanEventId={}, countryCode={}, countryName={}, cityName={}",
                    event.getId(),
                    location.countryCode(),
                    location.countryName(),
                    location.cityName());
        }
    }
}

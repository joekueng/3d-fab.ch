package com.printcalculator.service.qr;

import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrScanEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QrScanGeoEnrichmentService {

    private final QrScanEventRepository qrScanEventRepository;
    private final GeoLite2CityService geoLite2CityService;

    public QrScanGeoEnrichmentService(QrScanEventRepository qrScanEventRepository,
                                      GeoLite2CityService geoLite2CityService) {
        this.qrScanEventRepository = qrScanEventRepository;
        this.geoLite2CityService = geoLite2CityService;
    }

    @Transactional
    public void enrich(UUID qrScanEventId, String clientIp) {
        if (qrScanEventId == null) {
            return;
        }

        geoLite2CityService.lookup(clientIp)
                .ifPresent(location -> qrScanEventRepository.findById(qrScanEventId)
                        .ifPresent(event -> applyLocation(event, location)));
    }

    private void applyLocation(QrScanEvent event, GeoLite2CityService.GeoLocation location) {
        event.setCountryCode(location.countryCode());
        event.setCountryName(location.countryName());
        event.setCityName(location.cityName());
        qrScanEventRepository.save(event);
    }
}

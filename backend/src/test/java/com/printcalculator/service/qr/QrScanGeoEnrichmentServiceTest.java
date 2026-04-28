package com.printcalculator.service.qr;

import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrScanEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrScanGeoEnrichmentServiceTest {

    @Mock
    private QrScanEventRepository qrScanEventRepository;
    @Mock
    private GeoLite2CityService geoLite2CityService;

    private QrScanGeoEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new QrScanGeoEnrichmentService(qrScanEventRepository, geoLite2CityService, false);
    }

    @Test
    void enrich_shouldApplyGeoLiteLocationToScan() {
        UUID eventId = UUID.randomUUID();
        QrScanEvent event = new QrScanEvent();

        when(geoLite2CityService.lookup("8.8.8.8")).thenReturn(
                Optional.of(new GeoLite2CityService.GeoLocation("CH", "Svizzera", "Ticino", "Lugano"))
        );
        when(qrScanEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        service.enrich(eventId, "8.8.8.8");

        assertEquals("CH", event.getCountryCode());
        assertEquals("Svizzera", event.getCountryName());
        assertEquals("Ticino", event.getRegionName());
        assertEquals("Lugano", event.getCityName());
        verify(qrScanEventRepository).save(event);
    }

    @Test
    void enrich_shouldSkipLookupWhenLocationAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        QrScanEvent event = new QrScanEvent();
        event.setCountryCode("CH");

        when(qrScanEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        service.enrich(eventId, "8.8.8.8");

        verifyNoInteractions(geoLite2CityService);
    }
}

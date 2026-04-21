package com.printcalculator.service.qr;

import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrScanEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrScanGeoEnrichmentServiceTest {

    @Mock
    private QrScanEventRepository qrScanEventRepository;
    @Mock
    private GeoLite2CityService geoLite2CityService;

    @InjectMocks
    private QrScanGeoEnrichmentService service;

    @Test
    void enrich_shouldApplyGeoLiteLocationToScan() {
        UUID eventId = UUID.randomUUID();
        QrScanEvent event = new QrScanEvent();

        when(geoLite2CityService.lookup("8.8.8.8")).thenReturn(
                Optional.of(new GeoLite2CityService.GeoLocation("CH", "Svizzera", "Lugano"))
        );
        when(qrScanEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        service.enrich(eventId, "8.8.8.8");

        assertEquals("CH", event.getCountryCode());
        assertEquals("Svizzera", event.getCountryName());
        assertEquals("Lugano", event.getCityName());
        verify(qrScanEventRepository).save(event);
    }
}

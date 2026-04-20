package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminQrLinkStatsDto;
import com.printcalculator.dto.AdminQrOverviewStatsDto;
import com.printcalculator.entity.QrLink;
import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrLinkRepository;
import com.printcalculator.repository.QrScanEventRepository;
import com.printcalculator.service.qr.QrLinkSupportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminQrControllerServiceTest {

    @Mock
    private QrLinkRepository qrLinkRepository;
    @Mock
    private QrScanEventRepository qrScanEventRepository;
    @Mock
    private QrLinkSupportService qrLinkSupportService;

    @InjectMocks
    private AdminQrControllerService service;

    @Test
    void getOverviewStats_shouldAggregateVisibleEventsByQrLink() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        QrLink first = new QrLink();
        first.setId(firstId);
        first.setName("Flyer");
        first.setSlug("flyer");
        first.setTargetPath("/contact");
        first.setIsActive(true);

        QrLink second = new QrLink();
        second.setId(secondId);
        second.setName("Sticker");
        second.setSlug("sticker");
        second.setTargetPath("/about");
        second.setIsActive(false);

        QrScanEvent eventOne = new QrScanEvent();
        eventOne.setQrLink(first);
        eventOne.setScannedAt(OffsetDateTime.parse("2026-04-19T12:00:00+02:00"));
        eventOne.setVisitorKeyHash("a");
        eventOne.setIsSuspectedBot(false);
        eventOne.setCountryCode("CH");
        eventOne.setCountryName("Switzerland");
        eventOne.setCityName("Zurich");

        QrScanEvent eventTwo = new QrScanEvent();
        eventTwo.setQrLink(first);
        eventTwo.setScannedAt(OffsetDateTime.parse("2026-04-19T13:00:00+02:00"));
        eventTwo.setVisitorKeyHash("a");
        eventTwo.setIsSuspectedBot(false);
        eventTwo.setCountryCode("CH");
        eventTwo.setCountryName("Switzerland");
        eventTwo.setCityName("Zurich");

        QrScanEvent eventThree = new QrScanEvent();
        eventThree.setQrLink(second);
        eventThree.setScannedAt(OffsetDateTime.parse("2026-04-20T09:00:00+02:00"));
        eventThree.setVisitorKeyHash("b");
        eventThree.setIsSuspectedBot(false);
        eventThree.setCountryCode("IT");
        eventThree.setCountryName("Italy");
        eventThree.setCityName("Milan");

        when(qrLinkRepository.findAll(any(Sort.class))).thenReturn(List.of(first, second));
        when(qrScanEventRepository.findByScannedAtBetweenOrderByScannedAtDesc(any(), any()))
                .thenReturn(List.of(eventThree, eventTwo, eventOne));
        when(qrLinkSupportService.buildPublicUrl("flyer")).thenReturn("https://3d-fab.ch/api/public/qr/flyer");
        when(qrLinkSupportService.buildPublicUrl("sticker")).thenReturn("https://3d-fab.ch/api/public/qr/sticker");

        AdminQrOverviewStatsDto overview = service.getOverviewStats(
                LocalDate.of(2026, 4, 19),
                LocalDate.of(2026, 4, 20)
        );

        assertEquals(2, overview.getTotalQrLinks());
        assertEquals(1, overview.getActiveQrLinks());
        assertEquals(3, overview.getRawScans());
        assertEquals(2, overview.getUniqueVisitors());
        assertEquals(2, overview.getQrLinks().size());
        assertEquals("Flyer", overview.getQrLinks().get(0).getName());
        assertEquals(2, overview.getQrLinks().get(0).getRawScans());
        assertEquals(1, overview.getQrLinks().get(0).getUniqueVisitors());
        assertEquals("Zurich, Switzerland", overview.getQrLinks().get(0).getTopLocationLabel());
        assertEquals(2, overview.getQrLinks().get(0).getTopLocationScans());
        assertEquals(2, overview.getLocations().size());
        assertEquals("Zurich, Switzerland", overview.getLocations().get(0).getLabel());
        assertEquals(2, overview.getLocations().get(0).getScans());
    }

    @Test
    void getQrLinkStats_shouldExposeAggregatedLocations() {
        UUID qrLinkId = UUID.randomUUID();

        QrLink qrLink = new QrLink();
        qrLink.setId(qrLinkId);
        qrLink.setName("Flyer");
        qrLink.setSlug("flyer");
        qrLink.setTargetPath("/contact");
        qrLink.setIsActive(true);

        QrScanEvent eventOne = new QrScanEvent();
        eventOne.setQrLink(qrLink);
        eventOne.setScannedAt(OffsetDateTime.parse("2026-04-19T12:00:00+02:00"));
        eventOne.setVisitorKeyHash("a");
        eventOne.setResolvedLang("de");
        eventOne.setIsSuspectedBot(false);
        eventOne.setCountryCode("CH");
        eventOne.setCountryName("Switzerland");
        eventOne.setCityName("Zurich");

        QrScanEvent eventTwo = new QrScanEvent();
        eventTwo.setQrLink(qrLink);
        eventTwo.setScannedAt(OffsetDateTime.parse("2026-04-19T13:00:00+02:00"));
        eventTwo.setVisitorKeyHash("b");
        eventTwo.setResolvedLang("de");
        eventTwo.setIsSuspectedBot(false);
        eventTwo.setCountryCode("CH");
        eventTwo.setCountryName("Switzerland");
        eventTwo.setCityName("Zurich");

        when(qrLinkRepository.findById(qrLinkId)).thenReturn(Optional.of(qrLink));
        when(qrScanEventRepository.findByQrLink_IdAndScannedAtBetweenOrderByScannedAtDesc(any(), any(), any()))
                .thenReturn(List.of(eventTwo, eventOne));

        AdminQrLinkStatsDto stats = service.getQrLinkStats(
                qrLinkId,
                LocalDate.of(2026, 4, 19),
                LocalDate.of(2026, 4, 19)
        );

        assertEquals(1, stats.getLocations().size());
        assertEquals("Zurich, Switzerland", stats.getLocations().get(0).getLabel());
        assertEquals(2, stats.getLocations().get(0).getScans());
        assertEquals("Zurich", stats.getRecentScans().get(0).getCityName());
    }

    @Test
    void getQrLinkStats_shouldReturnNotFoundForMissingLink() {
        UUID missingId = UUID.randomUUID();
        when(qrLinkRepository.findById(missingId)).thenReturn(Optional.empty());

        org.springframework.web.server.ResponseStatusException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> service.getQrLinkStats(missingId, null, null)
                );

        assertEquals(404, ex.getStatusCode().value());
    }
}

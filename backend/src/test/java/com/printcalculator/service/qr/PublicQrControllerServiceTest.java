package com.printcalculator.service.qr;

import com.printcalculator.entity.QrLink;
import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrLinkRepository;
import com.printcalculator.repository.QrScanEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicQrControllerServiceTest {

    @Mock
    private QrLinkRepository qrLinkRepository;
    @Mock
    private QrScanEventRepository qrScanEventRepository;
    @Mock
    private QrLinkSupportService qrLinkSupportService;
    @Mock
    private QrTrackingRequestService qrTrackingRequestService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PublicQrControllerService service;

    @Test
    void resolveRedirect_shouldTrackAndReturnLocalizedPath() {
        UUID qrLinkId = UUID.randomUUID();
        QrLink qrLink = new QrLink();
        qrLink.setId(qrLinkId);
        qrLink.setSlug("flyer");
        qrLink.setTargetPath("/contact");
        qrLink.setIsActive(true);

        when(qrLinkSupportService.normalizeSlug("flyer")).thenReturn("flyer");
        when(qrLinkRepository.findBySlug("flyer")).thenReturn(Optional.of(qrLink));
        when(request.getHeader("Accept-Language")).thenReturn("de-CH,de;q=0.9");
        when(qrLinkSupportService.resolveLanguage("de-CH,de;q=0.9")).thenReturn("de");
        when(qrLinkSupportService.buildLocalizedPath("/contact", "de")).thenReturn("/de/contact");
        when(qrTrackingRequestService.resolve(request, qrLinkId)).thenReturn(
                new QrTrackingRequestService.ResolvedTrackingContext(
                        "203.0.113.10",
                        "abc123",
                        false,
                        "CH",
                        "Switzerland",
                        "Lugano",
                        OffsetDateTime.parse("2026-04-20T09:00:00+02:00")
                )
        );

        PublicQrControllerService.QrRedirectResult result = service.resolveRedirect("flyer", request);

        assertEquals("/de/contact", result.finalPath());
        ArgumentCaptor<QrScanEvent> eventCaptor = ArgumentCaptor.forClass(QrScanEvent.class);
        verify(qrScanEventRepository).save(eventCaptor.capture());
        assertEquals("de", eventCaptor.getValue().getResolvedLang());
        assertEquals("abc123", eventCaptor.getValue().getVisitorKeyHash());
        assertEquals("CH", eventCaptor.getValue().getCountryCode());
    }

    @Test
    void resolveRedirect_shouldReturnNotFoundForInactiveLinks() {
        QrLink qrLink = new QrLink();
        qrLink.setIsActive(false);

        when(qrLinkSupportService.normalizeSlug("inactive")).thenReturn("inactive");
        when(qrLinkRepository.findBySlug("inactive")).thenReturn(Optional.of(qrLink));

        assertThrows(ResponseStatusException.class, () -> service.resolveRedirect("inactive", request));
    }
}

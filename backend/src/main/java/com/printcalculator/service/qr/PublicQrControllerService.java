package com.printcalculator.service.qr;

import com.printcalculator.entity.QrLink;
import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.event.QrScanRecordedEvent;
import com.printcalculator.repository.QrLinkRepository;
import com.printcalculator.repository.QrScanEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class PublicQrControllerService {
    private final QrLinkRepository qrLinkRepository;
    private final QrScanEventRepository qrScanEventRepository;
    private final QrLinkSupportService qrLinkSupportService;
    private final QrTrackingRequestService qrTrackingRequestService;
    private final GeoLite2CityService geoLite2CityService;
    private final ApplicationEventPublisher eventPublisher;

    public PublicQrControllerService(QrLinkRepository qrLinkRepository,
                                     QrScanEventRepository qrScanEventRepository,
                                     QrLinkSupportService qrLinkSupportService,
                                     QrTrackingRequestService qrTrackingRequestService,
                                     GeoLite2CityService geoLite2CityService,
                                     ApplicationEventPublisher eventPublisher) {
        this.qrLinkRepository = qrLinkRepository;
        this.qrScanEventRepository = qrScanEventRepository;
        this.qrLinkSupportService = qrLinkSupportService;
        this.qrTrackingRequestService = qrTrackingRequestService;
        this.geoLite2CityService = geoLite2CityService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public QrRedirectResult resolveRedirect(String slug, HttpServletRequest request) {
        String normalizedSlug = qrLinkSupportService.normalizeSlug(slug);
        QrLink qrLink = qrLinkRepository.findBySlug(normalizedSlug)
                .filter(link -> Boolean.TRUE.equals(link.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "QR link not found"));

        String language = qrLinkSupportService.resolveLanguage(request.getHeader("Accept-Language"));
        String finalPath = qrLinkSupportService.buildLocalizedPath(qrLink.getTargetPath(), language);
        QrTrackingRequestService.ResolvedTrackingContext trackingContext =
                qrTrackingRequestService.resolve(request, qrLink.getId());

        QrScanEvent event = new QrScanEvent();
        event.setQrLink(qrLink);
        event.setScannedAt(trackingContext.scannedAt());
        event.setResolvedLang(language.toLowerCase(Locale.ROOT));
        event.setFinalPath(finalPath);
        event.setVisitorKeyHash(trackingContext.visitorKeyHash());
        event.setIsSuspectedBot(trackingContext.suspectedBot());
        enrichLocation(event, trackingContext);
        QrScanEvent savedEvent = qrScanEventRepository.save(event);
        UUID qrScanEventId = savedEvent != null && savedEvent.getId() != null ? savedEvent.getId() : event.getId();
        eventPublisher.publishEvent(new QrScanRecordedEvent(qrScanEventId, trackingContext.clientIp()));

        return new QrRedirectResult(finalPath, language, qrLink.getId());
    }

    private void enrichLocation(QrScanEvent event, QrTrackingRequestService.ResolvedTrackingContext trackingContext) {
        if (event == null || trackingContext == null || trackingContext.suspectedBot()) {
            return;
        }

        try {
            geoLite2CityService.lookup(trackingContext.clientIp())
                    .ifPresent(location -> {
                        event.setCountryCode(location.countryCode());
                        event.setCountryName(location.countryName());
                        event.setRegionName(location.regionName());
                        event.setCityName(location.cityName());
                    });
        } catch (Exception ignored) {
            // Best effort only: QR redirect must not fail because GeoIP enrichment is unavailable.
        }
    }

    public record QrRedirectResult(String finalPath, String language, java.util.UUID qrLinkId) {
    }
}

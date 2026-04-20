package com.printcalculator.service.qr;

import com.printcalculator.entity.QrLink;
import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrLinkRepository;
import com.printcalculator.repository.QrScanEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class PublicQrControllerService {
    private final QrLinkRepository qrLinkRepository;
    private final QrScanEventRepository qrScanEventRepository;
    private final QrLinkSupportService qrLinkSupportService;
    private final QrTrackingRequestService qrTrackingRequestService;

    public PublicQrControllerService(QrLinkRepository qrLinkRepository,
                                     QrScanEventRepository qrScanEventRepository,
                                     QrLinkSupportService qrLinkSupportService,
                                     QrTrackingRequestService qrTrackingRequestService) {
        this.qrLinkRepository = qrLinkRepository;
        this.qrScanEventRepository = qrScanEventRepository;
        this.qrLinkSupportService = qrLinkSupportService;
        this.qrTrackingRequestService = qrTrackingRequestService;
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
        event.setCountryCode(trackingContext.countryCode());
        event.setCountryName(trackingContext.countryName());
        event.setCityName(trackingContext.cityName());
        qrScanEventRepository.save(event);

        return new QrRedirectResult(finalPath, language, qrLink.getId());
    }

    public record QrRedirectResult(String finalPath, String language, java.util.UUID qrLinkId) {
    }
}

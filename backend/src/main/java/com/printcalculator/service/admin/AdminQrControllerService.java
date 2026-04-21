package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminQrDailyStatDto;
import com.printcalculator.dto.AdminQrDailyBreakdownDto;
import com.printcalculator.dto.AdminQrLanguageStatDto;
import com.printcalculator.dto.AdminQrLinkDto;
import com.printcalculator.dto.AdminQrLocationStatDto;
import com.printcalculator.dto.AdminQrOverviewItemDto;
import com.printcalculator.dto.AdminQrOverviewStatsDto;
import com.printcalculator.dto.AdminQrLinkStatsDto;
import com.printcalculator.dto.AdminQrScanEventDto;
import com.printcalculator.dto.AdminUpsertQrLinkRequest;
import com.printcalculator.entity.QrLink;
import com.printcalculator.entity.QrScanEvent;
import com.printcalculator.repository.QrLinkRepository;
import com.printcalculator.repository.QrScanEventRepository;
import com.printcalculator.service.qr.QrLinkSupportService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AdminQrControllerService {
    private static final ZoneId APP_ZONE = ZoneId.systemDefault();

    private final QrLinkRepository qrLinkRepository;
    private final QrScanEventRepository qrScanEventRepository;
    private final QrLinkSupportService qrLinkSupportService;

    public AdminQrControllerService(QrLinkRepository qrLinkRepository,
                                    QrScanEventRepository qrScanEventRepository,
                                    QrLinkSupportService qrLinkSupportService) {
        this.qrLinkRepository = qrLinkRepository;
        this.qrScanEventRepository = qrScanEventRepository;
        this.qrLinkSupportService = qrLinkSupportService;
    }

    public List<AdminQrLinkDto> listQrLinks() {
        return qrLinkRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toQrLinkDto)
                .toList();
    }

    @Transactional
    public AdminQrLinkDto createQrLink(AdminUpsertQrLinkRequest payload) {
        QrLink qrLink = new QrLink();
        applyPayload(qrLink, payload, null);
        return toQrLinkDto(qrLinkRepository.save(qrLink));
    }

    @Transactional
    public AdminQrLinkDto updateQrLink(UUID qrLinkId, AdminUpsertQrLinkRequest payload) {
        QrLink qrLink = qrLinkRepository.findById(qrLinkId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "QR link not found"));
        applyPayload(qrLink, payload, qrLinkId);
        return toQrLinkDto(qrLinkRepository.save(qrLink));
    }

    public AdminQrLinkStatsDto getQrLinkStats(UUID qrLinkId, LocalDate from, LocalDate to) {
        QrLink qrLink = qrLinkRepository.findById(qrLinkId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "QR link not found"));

        DateRange dateRange = resolveDateRange(from, to);
        LocalDate fromDate = dateRange.fromDate();
        LocalDate toDate = dateRange.toDate();

        List<QrScanEvent> allEvents = qrScanEventRepository
                .findByQrLink_IdAndScannedAtBetweenOrderByScannedAtDesc(
                        qrLinkId,
                        dateRange.fromInclusive(),
                        dateRange.toExclusive()
                );

        List<QrScanEvent> visibleEvents = allEvents.stream()
                .filter(event -> !Boolean.TRUE.equals(event.getIsSuspectedBot()))
                .toList();

        Map<LocalDate, Set<String>> uniqueVisitorsByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> scansByDay = new LinkedHashMap<>();
        Map<String, Long> scansByLanguage = new LinkedHashMap<>();
        Map<LocationKey, Long> scansByLocation = new LinkedHashMap<>();
        Set<String> uniqueVisitors = new LinkedHashSet<>();

        for (QrScanEvent event : visibleEvents) {
            LocalDate eventDate = event.getScannedAt().atZoneSameInstant(APP_ZONE).toLocalDate();
            scansByDay.merge(eventDate, 1L, Long::sum);
            uniqueVisitorsByDay.computeIfAbsent(eventDate, ignored -> new LinkedHashSet<>())
                    .add(event.getVisitorKeyHash());
            uniqueVisitors.add(event.getVisitorKeyHash());
            String language = String.valueOf(event.getResolvedLang() == null ? "" : event.getResolvedLang())
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!language.isBlank()) {
                scansByLanguage.merge(language, 1L, Long::sum);
            }
            mergeLocation(scansByLocation, event);
        }

        List<AdminQrDailyStatDto> dailyStats = fromDate.datesUntil(toDate.plusDays(1))
                .map(date -> {
                    AdminQrDailyStatDto dto = new AdminQrDailyStatDto();
                    dto.setDate(date);
                    dto.setScans(scansByDay.getOrDefault(date, 0L));
                    dto.setUniqueVisitors(uniqueVisitorsByDay.getOrDefault(date, Set.of()).size());
                    return dto;
                })
                .toList();

        List<AdminQrLanguageStatDto> languageStats = scansByLanguage.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    AdminQrLanguageStatDto dto = new AdminQrLanguageStatDto();
                    dto.setLanguage(entry.getKey());
                    dto.setScans(entry.getValue());
                    return dto;
                })
                .toList();

        List<AdminQrScanEventDto> recentScans = visibleEvents.stream()
                .limit(20)
                .map(this::toQrScanEventDto)
                .toList();

        AdminQrLinkStatsDto dto = new AdminQrLinkStatsDto();
        dto.setQrLinkId(qrLink.getId());
        dto.setFromDate(fromDate);
        dto.setToDate(toDate);
        dto.setRawScans(visibleEvents.size());
        dto.setUniqueVisitors(uniqueVisitors.size());
        dto.setExcludedBotScans(allEvents.size() - visibleEvents.size());
        dto.setLastScannedAt(visibleEvents.isEmpty() ? null : visibleEvents.get(0).getScannedAt());
        dto.setDaily(dailyStats);
        dto.setLanguages(languageStats);
        dto.setLocations(toLocationStats(scansByLocation));
        dto.setRecentScans(recentScans);
        return dto;
    }

    public AdminQrOverviewStatsDto getOverviewStats(LocalDate from, LocalDate to) {
        DateRange dateRange = resolveDateRange(from, to);
        List<QrLink> qrLinks = qrLinkRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        Map<UUID, QrLink> qrLinksById = qrLinks.stream()
                .collect(java.util.stream.Collectors.toMap(QrLink::getId, qrLink -> qrLink));
        List<QrScanEvent> visibleEvents = qrScanEventRepository
                .findByScannedAtBetweenOrderByScannedAtDesc(dateRange.fromInclusive(), dateRange.toExclusive())
                .stream()
                .filter(event -> !Boolean.TRUE.equals(event.getIsSuspectedBot()))
                .toList();

        Map<UUID, List<QrScanEvent>> eventsByQrLink = new HashMap<>();
        Map<LocalDate, Set<String>> uniqueVisitorsByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> scansByDay = new LinkedHashMap<>();
        Map<LocalDate, Map<UUID, Long>> scansByDayAndQr = new LinkedHashMap<>();
        Map<LocationKey, Long> scansByLocation = new LinkedHashMap<>();
        Set<String> uniqueVisitors = new LinkedHashSet<>();

        for (QrScanEvent event : visibleEvents) {
            UUID qrLinkId = event.getQrLink().getId();
            eventsByQrLink.computeIfAbsent(qrLinkId, ignored -> new ArrayList<>()).add(event);

            LocalDate eventDate = event.getScannedAt().atZoneSameInstant(APP_ZONE).toLocalDate();
            scansByDay.merge(eventDate, 1L, Long::sum);
            scansByDayAndQr.computeIfAbsent(eventDate, ignored -> new LinkedHashMap<>())
                    .merge(qrLinkId, 1L, Long::sum);
            uniqueVisitorsByDay.computeIfAbsent(eventDate, ignored -> new LinkedHashSet<>())
                    .add(event.getVisitorKeyHash());
            uniqueVisitors.add(event.getVisitorKeyHash());
            mergeLocation(scansByLocation, event);
        }

        List<AdminQrOverviewItemDto> qrOverview = qrLinks.stream()
                .map(qrLink -> {
                    List<QrScanEvent> qrEvents = eventsByQrLink.getOrDefault(qrLink.getId(), List.of());
                    Set<String> qrUniqueVisitors = qrEvents.stream()
                            .map(QrScanEvent::getVisitorKeyHash)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    Map<LocationKey, Long> qrLocationCounts = new LinkedHashMap<>();
                    qrEvents.forEach(event -> mergeLocation(qrLocationCounts, event));
                    Map.Entry<LocationKey, Long> topLocation = qrLocationCounts.entrySet().stream()
                            .sorted(Map.Entry.<LocationKey, Long>comparingByValue().reversed()
                                    .thenComparing(entry -> new LocationStat(entry.getKey(), entry.getValue()).label(),
                                            String.CASE_INSENSITIVE_ORDER))
                            .findFirst()
                            .orElse(null);

                    AdminQrOverviewItemDto dto = new AdminQrOverviewItemDto();
                    dto.setQrLinkId(qrLink.getId());
                    dto.setName(qrLink.getName());
                    dto.setSlug(qrLink.getSlug());
                    dto.setTargetPath(qrLink.getTargetPath());
                    dto.setIsActive(qrLink.getIsActive());
                    dto.setPublicUrl(qrLinkSupportService.buildPublicUrl(qrLink.getSlug()));
                    dto.setRawScans(qrEvents.size());
                    dto.setUniqueVisitors(qrUniqueVisitors.size());
                    dto.setTopLocationLabel(topLocation == null
                            ? null
                            : new LocationStat(topLocation.getKey(), topLocation.getValue()).label());
                    dto.setTopLocationScans(topLocation == null ? 0 : topLocation.getValue());
                    dto.setLastScannedAt(qrEvents.isEmpty() ? null : qrEvents.get(0).getScannedAt());
                    return dto;
                })
                .sorted(Comparator.comparingLong(AdminQrOverviewItemDto::getRawScans).reversed()
                        .thenComparing(AdminQrOverviewItemDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<UUID, Integer> qrDisplayOrder = new HashMap<>();
        for (int index = 0; index < qrOverview.size(); index++) {
            qrDisplayOrder.put(qrOverview.get(index).getQrLinkId(), index);
        }

        List<AdminQrDailyStatDto> dailyStats = dateRange.fromDate().datesUntil(dateRange.toDate().plusDays(1))
                .map(date -> {
                    AdminQrDailyStatDto dto = new AdminQrDailyStatDto();
                    dto.setDate(date);
                    dto.setScans(scansByDay.getOrDefault(date, 0L));
                    dto.setUniqueVisitors(uniqueVisitorsByDay.getOrDefault(date, Set.of()).size());
                    dto.setQrBreakdown(toDailyBreakdown(
                            scansByDayAndQr.getOrDefault(date, Map.of()),
                            qrLinksById,
                            qrDisplayOrder
                    ));
                    return dto;
                })
                .toList();

        AdminQrOverviewStatsDto dto = new AdminQrOverviewStatsDto();
        dto.setFromDate(dateRange.fromDate());
        dto.setToDate(dateRange.toDate());
        dto.setTotalQrLinks(qrLinks.size());
        dto.setActiveQrLinks((int) qrLinks.stream().filter(link -> Boolean.TRUE.equals(link.getIsActive())).count());
        dto.setRawScans(visibleEvents.size());
        dto.setUniqueVisitors(uniqueVisitors.size());
        dto.setDaily(dailyStats);
        dto.setLocations(toLocationStats(scansByLocation));
        dto.setQrLinks(qrOverview);
        return dto;
    }

    private List<AdminQrDailyBreakdownDto> toDailyBreakdown(Map<UUID, Long> scansByQr,
                                                            Map<UUID, QrLink> qrLinksById,
                                                            Map<UUID, Integer> qrDisplayOrder) {
        return scansByQr.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Comparator.<Map.Entry<UUID, Long>>comparingInt(
                                entry -> qrDisplayOrder.getOrDefault(entry.getKey(), Integer.MAX_VALUE))
                        .thenComparing(entry -> {
                            QrLink qrLink = qrLinksById.get(entry.getKey());
                            return qrLink == null ? "" : qrLink.getName();
                        }, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    QrLink qrLink = qrLinksById.get(entry.getKey());
                    AdminQrDailyBreakdownDto dto = new AdminQrDailyBreakdownDto();
                    dto.setQrLinkId(entry.getKey());
                    dto.setName(qrLink == null ? "QR sconosciuto" : qrLink.getName());
                    dto.setSlug(qrLink == null ? null : qrLink.getSlug());
                    dto.setScans(entry.getValue());
                    return dto;
                })
                .toList();
    }

    public String generateQrSvg(UUID qrLinkId) {
        QrLink qrLink = qrLinkRepository.findById(qrLinkId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "QR link not found"));
        return qrLinkSupportService.generateSvgForPublicUrl(qrLink.getSlug());
    }

    public String generateQrSvgFilename(UUID qrLinkId) {
        QrLink qrLink = qrLinkRepository.findById(qrLinkId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "QR link not found"));
        return qrLinkSupportService.defaultSvgFilename(qrLink.getSlug());
    }

    private void applyPayload(QrLink qrLink, AdminUpsertQrLinkRequest payload, UUID currentId) {
        if (payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Payload is required");
        }

        String normalizedSlug = qrLinkSupportService.normalizeSlug(payload.getSlug());
        QrLink existingWithSlug = qrLinkRepository.findBySlug(normalizedSlug).orElse(null);
        if (existingWithSlug != null && (currentId == null || !existingWithSlug.getId().equals(currentId))) {
            throw new ResponseStatusException(CONFLICT, "Slug already in use");
        }

        qrLink.setName(qrLinkSupportService.normalizeName(payload.getName()));
        qrLink.setSlug(normalizedSlug);
        qrLink.setTargetPath(qrLinkSupportService.normalizeTargetPath(payload.getTargetPath()));
        qrLink.setIsActive(qrLinkSupportService.normalizeActive(payload.getIsActive()));
        qrLink.setNotes(qrLinkSupportService.normalizeNotes(payload.getNotes()));
    }

    private AdminQrLinkDto toQrLinkDto(QrLink qrLink) {
        AdminQrLinkDto dto = new AdminQrLinkDto();
        dto.setId(qrLink.getId());
        dto.setName(qrLink.getName());
        dto.setSlug(qrLink.getSlug());
        dto.setTargetPath(qrLink.getTargetPath());
        dto.setIsActive(qrLink.getIsActive());
        dto.setNotes(qrLink.getNotes());
        dto.setPublicUrl(qrLinkSupportService.buildPublicUrl(qrLink.getSlug()));
        dto.setCreatedAt(qrLink.getCreatedAt());
        dto.setUpdatedAt(qrLink.getUpdatedAt());
        return dto;
    }

    private AdminQrScanEventDto toQrScanEventDto(QrScanEvent event) {
        AdminQrScanEventDto dto = new AdminQrScanEventDto();
        dto.setScannedAt(event.getScannedAt());
        dto.setResolvedLang(event.getResolvedLang());
        dto.setFinalPath(event.getFinalPath());
        dto.setCountryCode(event.getCountryCode());
        dto.setCountryName(event.getCountryName());
        dto.setRegionName(event.getRegionName());
        dto.setCityName(event.getCityName());
        return dto;
    }

    private void mergeLocation(Map<LocationKey, Long> scansByLocation, QrScanEvent event) {
        LocationKey key = toLocationKey(event);
        if (key == null) {
            return;
        }
        scansByLocation.merge(key, 1L, Long::sum);
    }

    private LocationKey toLocationKey(QrScanEvent event) {
        String countryCode = normalizeLocationPart(event.getCountryCode());
        String countryName = normalizeLocationPart(event.getCountryName());
        String regionName = normalizeLocationPart(event.getRegionName());
        String cityName = normalizeLocationPart(event.getCityName());
        if (countryCode == null && countryName == null && regionName == null && cityName == null) {
            return null;
        }
        return new LocationKey(countryCode, countryName, regionName, cityName);
    }

    private List<AdminQrLocationStatDto> toLocationStats(Map<LocationKey, Long> scansByLocation) {
        return scansByLocation.entrySet().stream()
                .map(entry -> new LocationStat(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(LocationStat::scans).reversed()
                        .thenComparing(LocationStat::label, String.CASE_INSENSITIVE_ORDER))
                .map(location -> {
                    AdminQrLocationStatDto dto = new AdminQrLocationStatDto();
                    dto.setCountryCode(location.key().countryCode());
                    dto.setCountryName(location.key().countryName());
                    dto.setRegionName(location.key().regionName());
                    dto.setCityName(location.key().cityName());
                    dto.setLabel(location.label());
                    dto.setScans(location.scans());
                    return dto;
                })
                .toList();
    }

    private String normalizeLocationPart(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private record LocationKey(String countryCode, String countryName, String regionName, String cityName) {
    }

    private record LocationStat(LocationKey key, long scans) {
        private String label() {
            if (key.cityName() != null && key.countryName() != null) {
                return key.cityName() + ", " + key.countryName();
            }
            if (key.cityName() != null && key.countryCode() != null) {
                return key.cityName() + ", " + key.countryCode();
            }
            if (key.regionName() != null && key.countryName() != null) {
                return key.regionName() + ", " + key.countryName();
            }
            if (key.regionName() != null && key.countryCode() != null) {
                return key.regionName() + ", " + key.countryCode();
            }
            if (key.countryName() != null) {
                return key.countryName();
            }
            return key.countryCode();
        }
    }

    private DateRange resolveDateRange(LocalDate from, LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now(APP_ZONE);
        LocalDate fromDate = from != null ? from : toDate.minusDays(29);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(BAD_REQUEST, "from must be <= to");
        }

        OffsetDateTime fromInclusive = fromDate.atStartOfDay(APP_ZONE).toOffsetDateTime();
        OffsetDateTime toExclusive = toDate.plusDays(1).atStartOfDay(APP_ZONE).toOffsetDateTime();
        return new DateRange(fromDate, toDate, fromInclusive, toExclusive);
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive
    ) {
    }
}

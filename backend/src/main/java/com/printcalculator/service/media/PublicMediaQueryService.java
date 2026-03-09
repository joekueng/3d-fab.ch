package com.printcalculator.service.media;

import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.dto.PublicMediaVariantDto;
import com.printcalculator.entity.MediaAsset;
import com.printcalculator.entity.MediaUsage;
import com.printcalculator.entity.MediaVariant;
import com.printcalculator.repository.MediaUsageRepository;
import com.printcalculator.repository.MediaVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PublicMediaQueryService {

    private static final String STATUS_READY = "READY";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String FORMAT_JPEG = "JPEG";
    private static final String FORMAT_WEBP = "WEBP";
    private static final String FORMAT_AVIF = "AVIF";

    private final MediaUsageRepository mediaUsageRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaStorageService mediaStorageService;

    public PublicMediaQueryService(MediaUsageRepository mediaUsageRepository,
                                   MediaVariantRepository mediaVariantRepository,
                                   MediaStorageService mediaStorageService) {
        this.mediaUsageRepository = mediaUsageRepository;
        this.mediaVariantRepository = mediaVariantRepository;
        this.mediaStorageService = mediaStorageService;
    }

    public List<PublicMediaUsageDto> getUsageMedia(String usageType, String usageKey) {
        String normalizedUsageType = normalizeUsageType(usageType);
        String normalizedUsageKey = normalizeUsageKey(usageKey);

        List<MediaUsage> usages = mediaUsageRepository
                .findByUsageTypeAndUsageKeyAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(
                        normalizedUsageType,
                        normalizedUsageKey
                )
                .stream()
                .filter(this::isPublicReadyUsage)
                .sorted(Comparator
                        .comparing(MediaUsage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MediaUsage::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo)))
                .toList();

        if (usages.isEmpty()) {
            return List.of();
        }

        List<UUID> assetIds = usages.stream()
                .map(MediaUsage::getMediaAsset)
                .filter(Objects::nonNull)
                .map(MediaAsset::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, List<MediaVariant>> variantsByAssetId = mediaVariantRepository.findByMediaAsset_IdIn(assetIds)
                .stream()
                .filter(variant -> !Objects.equals("ORIGINAL", variant.getFormat()))
                .collect(Collectors.groupingBy(variant -> variant.getMediaAsset().getId()));

        return usages.stream()
                .map(usage -> toDto(
                        usage,
                        variantsByAssetId.getOrDefault(usage.getMediaAsset().getId(), List.of())
                ))
                .toList();
    }

    private boolean isPublicReadyUsage(MediaUsage usage) {
        MediaAsset asset = usage.getMediaAsset();
        return asset != null
                && STATUS_READY.equals(asset.getStatus())
                && VISIBILITY_PUBLIC.equals(asset.getVisibility());
    }

    private PublicMediaUsageDto toDto(MediaUsage usage, List<MediaVariant> variants) {
        Map<String, Map<String, MediaVariant>> variantsByPresetAndFormat = variants.stream()
                .collect(Collectors.groupingBy(
                        MediaVariant::getVariantName,
                        Collectors.toMap(MediaVariant::getFormat, Function.identity(), (left, right) -> right)
                ));

        PublicMediaUsageDto dto = new PublicMediaUsageDto();
        dto.setMediaAssetId(usage.getMediaAsset().getId());
        dto.setTitle(usage.getMediaAsset().getTitle());
        dto.setAltText(usage.getMediaAsset().getAltText());
        dto.setUsageType(usage.getUsageType());
        dto.setUsageKey(usage.getUsageKey());
        dto.setSortOrder(usage.getSortOrder());
        dto.setIsPrimary(usage.getIsPrimary());
        dto.setThumb(buildPresetDto(variantsByPresetAndFormat.get("thumb")));
        dto.setCard(buildPresetDto(variantsByPresetAndFormat.get("card")));
        dto.setHero(buildPresetDto(variantsByPresetAndFormat.get("hero")));
        return dto;
    }

    private PublicMediaVariantDto buildPresetDto(Map<String, MediaVariant> variantsByFormat) {
        PublicMediaVariantDto dto = new PublicMediaVariantDto();
        if (variantsByFormat == null || variantsByFormat.isEmpty()) {
            return dto;
        }

        dto.setAvifUrl(buildVariantUrl(variantsByFormat.get(FORMAT_AVIF)));
        dto.setWebpUrl(buildVariantUrl(variantsByFormat.get(FORMAT_WEBP)));
        dto.setJpegUrl(buildVariantUrl(variantsByFormat.get(FORMAT_JPEG)));
        return dto;
    }

    private String buildVariantUrl(MediaVariant variant) {
        if (variant == null || variant.getStorageKey() == null || variant.getStorageKey().isBlank()) {
            return null;
        }
        return mediaStorageService.buildPublicUrl(variant.getStorageKey());
    }

    private String normalizeUsageType(String usageType) {
        if (usageType == null || usageType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usageType is required.");
        }
        return usageType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUsageKey(String usageKey) {
        if (usageKey == null || usageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usageKey is required.");
        }
        return usageKey.trim();
    }
}

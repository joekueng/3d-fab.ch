package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminCreateMediaUsageRequest;
import com.printcalculator.dto.AdminMediaAssetDto;
import com.printcalculator.dto.AdminMediaUsageDto;
import com.printcalculator.dto.AdminMediaVariantDto;
import com.printcalculator.dto.MediaTextTranslationDto;
import com.printcalculator.dto.AdminUpdateMediaAssetRequest;
import com.printcalculator.dto.AdminUpdateMediaUsageRequest;
import com.printcalculator.entity.MediaAsset;
import com.printcalculator.entity.MediaUsage;
import com.printcalculator.entity.MediaVariant;
import com.printcalculator.repository.MediaAssetRepository;
import com.printcalculator.repository.MediaUsageRepository;
import com.printcalculator.repository.MediaVariantRepository;
import com.printcalculator.service.media.MediaFfmpegService;
import com.printcalculator.service.media.MediaImageInspector;
import com.printcalculator.service.media.MediaStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminMediaControllerService {

    private static final Logger logger = LoggerFactory.getLogger(AdminMediaControllerService.class);

    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";

    private static final String FORMAT_ORIGINAL = "ORIGINAL";
    private static final String FORMAT_JPEG = "JPEG";
    private static final String FORMAT_WEBP = "WEBP";
    private static final String FORMAT_AVIF = "AVIF";

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_UPLOADED, STATUS_PROCESSING, STATUS_READY, STATUS_FAILED, STATUS_ARCHIVED
    );
    private static final Set<String> ALLOWED_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, VISIBILITY_PRIVATE);
    private static final Set<String> ALLOWED_UPLOAD_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final List<String> SUPPORTED_MEDIA_LANGUAGES = List.of("it", "en", "de", "fr");
    private static final Map<String, String> GENERATED_FORMAT_MIME_TYPES = Map.of(
            FORMAT_JPEG, "image/jpeg",
            FORMAT_WEBP, "image/webp",
            FORMAT_AVIF, "image/avif"
    );
    private static final Map<String, String> GENERATED_FORMAT_EXTENSIONS = Map.of(
            FORMAT_JPEG, "jpg",
            FORMAT_WEBP, "webp",
            FORMAT_AVIF, "avif"
    );
    private static final List<PresetDefinition> PRESETS = List.of(
            new PresetDefinition("thumb", 320),
            new PresetDefinition("card", 640),
            new PresetDefinition("hero", 1280)
    );
    private static final DateTimeFormatter STORAGE_FOLDER_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaVariantRepository mediaVariantRepository;
    private final MediaUsageRepository mediaUsageRepository;
    private final MediaStorageService mediaStorageService;
    private final MediaImageInspector mediaImageInspector;
    private final MediaFfmpegService mediaFfmpegService;
    private final ClamAVService clamAVService;
    private final long maxUploadFileSizeBytes;

    public AdminMediaControllerService(MediaAssetRepository mediaAssetRepository,
                                       MediaVariantRepository mediaVariantRepository,
                                       MediaUsageRepository mediaUsageRepository,
                                       MediaStorageService mediaStorageService,
                                       MediaImageInspector mediaImageInspector,
                                       MediaFfmpegService mediaFfmpegService,
                                       ClamAVService clamAVService,
                                       @Value("${media.upload.max-file-size-bytes:26214400}") long maxUploadFileSizeBytes) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaVariantRepository = mediaVariantRepository;
        this.mediaUsageRepository = mediaUsageRepository;
        this.mediaStorageService = mediaStorageService;
        this.mediaImageInspector = mediaImageInspector;
        this.mediaFfmpegService = mediaFfmpegService;
        this.clamAVService = clamAVService;
        this.maxUploadFileSizeBytes = maxUploadFileSizeBytes;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AdminMediaAssetDto uploadAsset(MultipartFile file,
                                          String title,
                                          String altText,
                                          String visibility) {
        validateUpload(file);

        Path tempDirectory = null;
        MediaAsset asset = null;

        try {
            String normalizedVisibility = normalizeVisibility(visibility, true);
            tempDirectory = Files.createTempDirectory("media-asset-");
            Path uploadFile = tempDirectory.resolve("upload.bin");
            file.transferTo(uploadFile);

            try (InputStream inputStream = Files.newInputStream(uploadFile)) {
                clamAVService.scan(inputStream);
            }

            MediaImageInspector.ImageMetadata metadata = mediaImageInspector.inspect(uploadFile);
            if (!ALLOWED_UPLOAD_MIME_TYPES.contains(metadata.mimeType())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported image type. Allowed: jpg, jpeg, png, webp."
                );
            }

            String storageFolder = buildStorageFolder();
            String originalStorageKey = storageFolder + "/original." + metadata.fileExtension();
            String normalizedFilename = sanitizeOriginalFilename(file.getOriginalFilename(), metadata.fileExtension());
            String normalizedTitle = normalizeText(title);
            String normalizedAltText = normalizeText(altText);
            long originalFileSize = Files.size(uploadFile);
            String sha256Hex = computeSha256(uploadFile);

            mediaStorageService.storeOriginal(uploadFile, originalStorageKey);

            OffsetDateTime now = OffsetDateTime.now();
            asset = new MediaAsset();
            asset.setOriginalFilename(normalizedFilename);
            asset.setStorageKey(originalStorageKey);
            asset.setMimeType(metadata.mimeType());
            asset.setFileSizeBytes(originalFileSize);
            asset.setSha256Hex(sha256Hex);
            asset.setWidthPx(metadata.widthPx());
            asset.setHeightPx(metadata.heightPx());
            asset.setStatus(STATUS_UPLOADED);
            asset.setVisibility(normalizedVisibility);
            asset.setTitle(normalizedTitle);
            asset.setAltText(normalizedAltText);
            asset.setCreatedAt(now);
            asset.setUpdatedAt(now);
            asset = mediaAssetRepository.save(asset);

            MediaVariant originalVariant = new MediaVariant();
            originalVariant.setMediaAsset(asset);
            originalVariant.setVariantName("original");
            originalVariant.setFormat(FORMAT_ORIGINAL);
            originalVariant.setStorageKey(originalStorageKey);
            originalVariant.setMimeType(metadata.mimeType());
            originalVariant.setWidthPx(metadata.widthPx());
            originalVariant.setHeightPx(metadata.heightPx());
            originalVariant.setFileSizeBytes(originalFileSize);
            originalVariant.setIsGenerated(false);
            originalVariant.setCreatedAt(now);
            mediaVariantRepository.save(originalVariant);

            asset.setStatus(STATUS_PROCESSING);
            asset.setUpdatedAt(OffsetDateTime.now());
            asset = mediaAssetRepository.save(asset);

            List<MediaVariant> generatedVariants = generateDerivedVariants(asset, uploadFile, tempDirectory);
            mediaVariantRepository.saveAll(generatedVariants);

            asset.setStatus(STATUS_READY);
            asset.setUpdatedAt(OffsetDateTime.now());
            mediaAssetRepository.save(asset);

            return getAsset(asset.getId());
        } catch (ResponseStatusException e) {
            markFailed(asset, e.getReason(), e);
            throw e;
        } catch (IOException e) {
            markFailed(asset, "Media processing failed.", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Media processing failed.");
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    public List<AdminMediaAssetDto> listAssets() {
        return toAssetDtos(mediaAssetRepository.findAllByOrderByCreatedAtDesc());
    }

    public AdminMediaAssetDto getAsset(UUID mediaAssetId) {
        MediaAsset asset = getAssetOrThrow(mediaAssetId);
        return toAssetDtos(List.of(asset)).getFirst();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AdminMediaAssetDto updateAsset(UUID mediaAssetId, AdminUpdateMediaAssetRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }

        MediaAsset asset = getAssetOrThrow(mediaAssetId);
        String requestedVisibility = normalizeVisibility(payload.getVisibility(), false);
        String requestedStatus = normalizeStatus(payload.getStatus(), false);

        if (requestedVisibility != null && !requestedVisibility.equals(asset.getVisibility())) {
            moveGeneratedVariants(asset, requestedVisibility);
            asset.setVisibility(requestedVisibility);
        }
        if (requestedStatus != null) {
            asset.setStatus(requestedStatus);
        }
        if (payload.getTitle() != null) {
            asset.setTitle(normalizeText(payload.getTitle()));
        }
        if (payload.getAltText() != null) {
            asset.setAltText(normalizeText(payload.getAltText()));
        }

        asset.setUpdatedAt(OffsetDateTime.now());
        mediaAssetRepository.save(asset);
        return getAsset(asset.getId());
    }

    @Transactional
    public AdminMediaUsageDto createUsage(AdminCreateMediaUsageRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }

        MediaAsset asset = getAssetOrThrow(payload.getMediaAssetId());
        String usageType = requireUsageType(payload.getUsageType());
        String usageKey = requireUsageKey(payload.getUsageKey());
        boolean isPrimary = Boolean.TRUE.equals(payload.getIsPrimary());
        Map<String, MediaTextTranslationDto> translations = requireTranslations(payload.getTranslations());

        if (isPrimary) {
            unsetPrimaryForScope(usageType, usageKey, payload.getOwnerId(), null);
        }

        MediaUsage usage = new MediaUsage();
        usage.setUsageType(usageType);
        usage.setUsageKey(usageKey);
        usage.setOwnerId(payload.getOwnerId());
        usage.setMediaAsset(asset);
        usage.setSortOrder(payload.getSortOrder() != null ? payload.getSortOrder() : 0);
        usage.setIsPrimary(isPrimary);
        usage.setIsActive(payload.getIsActive() == null || payload.getIsActive());
        usage.setCreatedAt(OffsetDateTime.now());
        applyTranslations(usage, translations);

        MediaUsage saved = mediaUsageRepository.save(usage);
        return toUsageDto(saved);
    }

    @Transactional
    public AdminMediaUsageDto updateUsage(UUID mediaUsageId, AdminUpdateMediaUsageRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required.");
        }

        MediaUsage usage = getUsageOrThrow(mediaUsageId);

        if (payload.getUsageType() != null) {
            usage.setUsageType(requireUsageType(payload.getUsageType()));
        }
        if (payload.getUsageKey() != null) {
            usage.setUsageKey(requireUsageKey(payload.getUsageKey()));
        }
        if (payload.getOwnerId() != null) {
            usage.setOwnerId(payload.getOwnerId());
        }
        if (payload.getMediaAssetId() != null) {
            usage.setMediaAsset(getAssetOrThrow(payload.getMediaAssetId()));
        }
        if (payload.getSortOrder() != null) {
            usage.setSortOrder(payload.getSortOrder());
        }
        if (payload.getIsActive() != null) {
            usage.setIsActive(payload.getIsActive());
        }
        if (payload.getIsPrimary() != null) {
            usage.setIsPrimary(payload.getIsPrimary());
        }
        if (payload.getTranslations() != null) {
            applyTranslations(usage, requireTranslations(payload.getTranslations()));
        }

        if (Boolean.TRUE.equals(usage.getIsPrimary())) {
            unsetPrimaryForScope(usage.getUsageType(), usage.getUsageKey(), usage.getOwnerId(), usage.getId());
        }

        MediaUsage saved = mediaUsageRepository.save(usage);
        return toUsageDto(saved);
    }

    @Transactional
    public void deleteUsage(UUID mediaUsageId) {
        mediaUsageRepository.delete(getUsageOrThrow(mediaUsageId));
    }

    public List<AdminMediaUsageDto> getUsages(String usageType, String usageKey, UUID ownerId) {
        String normalizedUsageType = requireUsageType(usageType);
        String normalizedUsageKey = requireUsageKey(usageKey);
        return mediaUsageRepository.findByUsageScope(normalizedUsageType, normalizedUsageKey, ownerId)
                .stream()
                .sorted(Comparator
                        .comparing(MediaUsage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MediaUsage::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo)))
                .map(this::toUsageDto)
                .toList();
    }

    private List<MediaVariant> generateDerivedVariants(MediaAsset asset, Path sourceFile, Path tempDirectory) throws IOException {
        Path generatedDirectory = Files.createDirectories(tempDirectory.resolve("generated"));
        String storageFolder = extractStorageFolder(asset.getStorageKey());

        List<PendingGeneratedVariant> pendingVariants = new ArrayList<>();
        Set<String> skippedFormats = new LinkedHashSet<>();
        for (PresetDefinition preset : PRESETS) {
            VariantDimensions dimensions = computeVariantDimensions(
                    asset.getWidthPx(),
                    asset.getHeightPx(),
                    preset.maxDimension()
            );

            for (String format : List.of(FORMAT_JPEG, FORMAT_WEBP, FORMAT_AVIF)) {
                if (!mediaFfmpegService.canEncode(format)) {
                    skippedFormats.add(format);
                    continue;
                }
                String extension = GENERATED_FORMAT_EXTENSIONS.get(format);
                Path outputFile = generatedDirectory.resolve(preset.name() + "." + extension);
                try {
                    mediaFfmpegService.generateVariant(
                            sourceFile,
                            outputFile,
                            dimensions.widthPx(),
                            dimensions.heightPx(),
                            format
                    );
                } catch (IOException e) {
                    if (FORMAT_AVIF.equals(format)) {
                        skippedFormats.add(format);
                        logger.warn(
                                "Skipping AVIF variant generation for asset {} preset '{}' because FFmpeg AVIF generation failed: {}",
                                asset.getId(),
                                preset.name(),
                                e.getMessage()
                        );
                        continue;
                    }
                    throw e;
                }

                MediaVariant variant = new MediaVariant();
                variant.setMediaAsset(asset);
                variant.setVariantName(preset.name());
                variant.setFormat(format);
                variant.setStorageKey(storageFolder + "/" + preset.name() + "." + extension);
                variant.setMimeType(GENERATED_FORMAT_MIME_TYPES.get(format));
                variant.setWidthPx(dimensions.widthPx());
                variant.setHeightPx(dimensions.heightPx());
                variant.setFileSizeBytes(Files.size(outputFile));
                variant.setIsGenerated(true);
                variant.setCreatedAt(OffsetDateTime.now());

                pendingVariants.add(new PendingGeneratedVariant(variant, outputFile));
            }
        }

        if (!skippedFormats.isEmpty()) {
            logger.warn(
                    "Skipping media formats for asset {} because FFmpeg support is unavailable: {}",
                    asset.getId(),
                    String.join(", ", skippedFormats)
            );
        }

        List<String> storedKeys = new ArrayList<>();
        try {
            for (PendingGeneratedVariant pendingVariant : pendingVariants) {
                storeGeneratedVariant(asset.getVisibility(), pendingVariant);
                storedKeys.add(pendingVariant.variant().getStorageKey());
            }
        } catch (IOException e) {
            cleanupStoredGeneratedVariants(asset.getVisibility(), storedKeys);
            throw e;
        }

        return pendingVariants.stream()
                .map(PendingGeneratedVariant::variant)
                .toList();
    }

    private void storeGeneratedVariant(String visibility, PendingGeneratedVariant pendingVariant) throws IOException {
        if (VISIBILITY_PUBLIC.equals(visibility)) {
            mediaStorageService.storePublic(pendingVariant.file(), pendingVariant.variant().getStorageKey());
            return;
        }
        mediaStorageService.storePrivate(pendingVariant.file(), pendingVariant.variant().getStorageKey());
    }

    private void cleanupStoredGeneratedVariants(String visibility, Collection<String> storageKeys) {
        for (String storageKey : storageKeys) {
            try {
                mediaStorageService.deleteGenerated(visibility, storageKey);
            } catch (IOException cleanupException) {
                logger.warn("Failed to clean up media variant {}", storageKey, cleanupException);
            }
        }
    }

    private void moveGeneratedVariants(MediaAsset asset, String requestedVisibility) {
        List<MediaVariant> variants = mediaVariantRepository.findByMediaAsset_IdOrderByCreatedAtAsc(asset.getId());
        List<String> movedStorageKeys = new ArrayList<>();
        try {
            for (MediaVariant variant : variants) {
                if (FORMAT_ORIGINAL.equals(variant.getFormat())) {
                    continue;
                }
                mediaStorageService.moveGenerated(variant.getStorageKey(), asset.getVisibility(), requestedVisibility);
                movedStorageKeys.add(variant.getStorageKey());
            }
        } catch (IOException e) {
            reverseMovedVariants(asset.getVisibility(), requestedVisibility, movedStorageKeys);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to move media files.");
        }
    }

    private void reverseMovedVariants(String originalVisibility, String requestedVisibility, List<String> movedStorageKeys) {
        List<String> reversedOrder = new ArrayList<>(movedStorageKeys);
        java.util.Collections.reverse(reversedOrder);
        for (String storageKey : reversedOrder) {
            try {
                mediaStorageService.moveGenerated(storageKey, requestedVisibility, originalVisibility);
            } catch (IOException reverseException) {
                logger.error("Failed to restore media variant {}", storageKey, reverseException);
            }
        }
    }

    private void unsetPrimaryForScope(String usageType, String usageKey, UUID ownerId, UUID excludeUsageId) {
        List<MediaUsage> existingUsages = mediaUsageRepository.findByUsageScope(usageType, usageKey, ownerId);
        List<MediaUsage> usagesToUpdate = existingUsages.stream()
                .filter(existing -> excludeUsageId == null || !existing.getId().equals(excludeUsageId))
                .filter(existing -> Boolean.TRUE.equals(existing.getIsPrimary()))
                .peek(existing -> existing.setIsPrimary(false))
                .toList();

        if (!usagesToUpdate.isEmpty()) {
            mediaUsageRepository.saveAll(usagesToUpdate);
        }
    }

    private List<AdminMediaAssetDto> toAssetDtos(List<MediaAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }

        List<UUID> assetIds = assets.stream()
                .map(MediaAsset::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, List<MediaVariant>> variantsByAssetId = mediaVariantRepository.findByMediaAsset_IdIn(assetIds)
                .stream()
                .sorted(this::compareVariants)
                .collect(Collectors.groupingBy(variant -> variant.getMediaAsset().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<UUID, List<MediaUsage>> usagesByAssetId = mediaUsageRepository.findByMediaAsset_IdIn(assetIds)
                .stream()
                .sorted(Comparator
                        .comparing(MediaUsage::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(MediaUsage::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo)))
                .collect(Collectors.groupingBy(usage -> usage.getMediaAsset().getId(), LinkedHashMap::new, Collectors.toList()));

        return assets.stream()
                .map(asset -> toAssetDto(
                        asset,
                        variantsByAssetId.getOrDefault(asset.getId(), List.of()),
                        usagesByAssetId.getOrDefault(asset.getId(), List.of())
                ))
                .toList();
    }

    private AdminMediaAssetDto toAssetDto(MediaAsset asset, List<MediaVariant> variants, List<MediaUsage> usages) {
        AdminMediaAssetDto dto = new AdminMediaAssetDto();
        dto.setId(asset.getId());
        dto.setOriginalFilename(asset.getOriginalFilename());
        dto.setStorageKey(asset.getStorageKey());
        dto.setMimeType(asset.getMimeType());
        dto.setFileSizeBytes(asset.getFileSizeBytes());
        dto.setSha256Hex(asset.getSha256Hex());
        dto.setWidthPx(asset.getWidthPx());
        dto.setHeightPx(asset.getHeightPx());
        dto.setStatus(asset.getStatus());
        dto.setVisibility(asset.getVisibility());
        dto.setTitle(asset.getTitle());
        dto.setAltText(asset.getAltText());
        dto.setCreatedAt(asset.getCreatedAt());
        dto.setUpdatedAt(asset.getUpdatedAt());
        dto.setVariants(variants.stream().map(variant -> toVariantDto(asset, variant)).toList());
        dto.setUsages(usages.stream().map(this::toUsageDto).toList());
        return dto;
    }

    private AdminMediaVariantDto toVariantDto(MediaAsset asset, MediaVariant variant) {
        AdminMediaVariantDto dto = new AdminMediaVariantDto();
        dto.setId(variant.getId());
        dto.setVariantName(variant.getVariantName());
        dto.setFormat(variant.getFormat());
        dto.setStorageKey(variant.getStorageKey());
        dto.setMimeType(variant.getMimeType());
        dto.setWidthPx(variant.getWidthPx());
        dto.setHeightPx(variant.getHeightPx());
        dto.setFileSizeBytes(variant.getFileSizeBytes());
        dto.setIsGenerated(variant.getIsGenerated());
        dto.setCreatedAt(variant.getCreatedAt());
        if (VISIBILITY_PUBLIC.equals(asset.getVisibility()) && !FORMAT_ORIGINAL.equals(variant.getFormat())) {
            dto.setPublicUrl(mediaStorageService.buildPublicUrl(variant.getStorageKey()));
        }
        return dto;
    }

    private AdminMediaUsageDto toUsageDto(MediaUsage usage) {
        AdminMediaUsageDto dto = new AdminMediaUsageDto();
        dto.setId(usage.getId());
        dto.setUsageType(usage.getUsageType());
        dto.setUsageKey(usage.getUsageKey());
        dto.setOwnerId(usage.getOwnerId());
        dto.setMediaAssetId(usage.getMediaAsset().getId());
        dto.setSortOrder(usage.getSortOrder());
        dto.setIsPrimary(usage.getIsPrimary());
        dto.setIsActive(usage.getIsActive());
        dto.setTranslations(extractTranslations(usage));
        dto.setCreatedAt(usage.getCreatedAt());
        return dto;
    }

    private int compareVariants(MediaVariant left, MediaVariant right) {
        return Comparator
                .comparingInt((MediaVariant variant) -> variantNameOrder(variant.getVariantName()))
                .thenComparingInt(variant -> formatOrder(variant.getFormat()))
                .thenComparing(MediaVariant::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo))
                .compare(left, right);
    }

    private int variantNameOrder(String variantName) {
        if ("original".equalsIgnoreCase(variantName)) {
            return 0;
        }
        if ("thumb".equalsIgnoreCase(variantName)) {
            return 10;
        }
        if ("card".equalsIgnoreCase(variantName)) {
            return 20;
        }
        if ("hero".equalsIgnoreCase(variantName)) {
            return 30;
        }
        return 100;
    }

    private int formatOrder(String format) {
        return switch (format) {
            case FORMAT_ORIGINAL -> 0;
            case FORMAT_JPEG -> 10;
            case FORMAT_WEBP -> 20;
            case FORMAT_AVIF -> 30;
            default -> 100;
        };
    }

    private MediaAsset getAssetOrThrow(UUID mediaAssetId) {
        if (mediaAssetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media asset id is required.");
        }
        return mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media asset not found."));
    }

    private MediaUsage getUsageOrThrow(UUID mediaUsageId) {
        return mediaUsageRepository.findById(mediaUsageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media usage not found."));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required.");
        }
        if (file.getSize() < 0 || file.getSize() > maxUploadFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file exceeds the maximum allowed size.");
        }
    }

    private String requireUsageType(String usageType) {
        if (usageType == null || usageType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usageType is required.");
        }
        return usageType.trim().toUpperCase(Locale.ROOT);
    }

    private String requireUsageKey(String usageKey) {
        if (usageKey == null || usageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usageKey is required.");
        }
        return usageKey.trim();
    }

    private String normalizeVisibility(String visibility, boolean defaultPublic) {
        if (visibility == null) {
            return defaultPublic ? VISIBILITY_PUBLIC : null;
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return defaultPublic ? VISIBILITY_PUBLIC : null;
        }
        if (!ALLOWED_VISIBILITIES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid visibility. Allowed: " + String.join(", ", new LinkedHashSet<>(ALLOWED_VISIBILITIES))
            );
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean required) {
        if (status == null) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required.");
            }
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required.");
            }
            return null;
        }
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status. Allowed: " + String.join(", ", new LinkedHashSet<>(ALLOWED_STATUSES))
            );
        }
        return normalized;
    }

    private Map<String, MediaTextTranslationDto> requireTranslations(Map<String, MediaTextTranslationDto> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "translations are required.");
        }

        Map<String, MediaTextTranslationDto> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, MediaTextTranslationDto> entry : translations.entrySet()) {
            String language = normalizeTranslationLanguage(entry.getKey());
            if (normalized.containsKey(language)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate translation language: " + language + ".");
            }
            normalized.put(language, entry.getValue());
        }

        if (!normalized.keySet().equals(new LinkedHashSet<>(SUPPORTED_MEDIA_LANGUAGES))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "translations must include exactly: " + String.join(", ", SUPPORTED_MEDIA_LANGUAGES) + "."
            );
        }

        LinkedHashMap<String, MediaTextTranslationDto> result = new LinkedHashMap<>();
        for (String language : SUPPORTED_MEDIA_LANGUAGES) {
            MediaTextTranslationDto translation = normalized.get(language);
            if (translation == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing translation for language " + language + ".");
            }

            String title = normalizeRequiredTranslationValue(translation.getTitle(), language, "title");
            String altText = normalizeRequiredTranslationValue(translation.getAltText(), language, "altText");

            MediaTextTranslationDto dto = new MediaTextTranslationDto();
            dto.setTitle(title);
            dto.setAltText(altText);
            result.put(language, dto);
        }
        return result;
    }

    private String normalizeTranslationLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Translation language is required.");
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_MEDIA_LANGUAGES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported translation language: " + normalized + "."
            );
        }
        return normalized;
    }

    private String normalizeRequiredTranslationValue(String value, String language, String fieldName) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Translation " + fieldName + " is required for language " + language + "."
            );
        }
        return normalized;
    }

    private void applyTranslations(MediaUsage usage, Map<String, MediaTextTranslationDto> translations) {
        for (String language : SUPPORTED_MEDIA_LANGUAGES) {
            MediaTextTranslationDto translation = translations.get(language);
            usage.setTitleForLanguage(language, translation.getTitle());
            usage.setAltTextForLanguage(language, translation.getAltText());
        }
    }

    private Map<String, MediaTextTranslationDto> extractTranslations(MediaUsage usage) {
        LinkedHashMap<String, MediaTextTranslationDto> translations = new LinkedHashMap<>();
        String fallbackTitle = usage.getMediaAsset() != null ? usage.getMediaAsset().getTitle() : null;
        String fallbackAltText = usage.getMediaAsset() != null ? usage.getMediaAsset().getAltText() : null;

        for (String language : SUPPORTED_MEDIA_LANGUAGES) {
            MediaTextTranslationDto dto = new MediaTextTranslationDto();
            dto.setTitle(firstNonBlank(usage.getTitleForLanguage(language), fallbackTitle));
            dto.setAltText(firstNonBlank(usage.getAltTextForLanguage(language), fallbackAltText));
            translations.put(language, dto);
        }
        return translations;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : normalizeText(fallback);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String sanitizeOriginalFilename(String originalFilename, String extension) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        int separatorIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String basename = separatorIndex >= 0 ? cleaned.substring(separatorIndex + 1) : cleaned;
        basename = basename.replace("\r", "_").replace("\n", "_");
        if (basename.isBlank()) {
            return "upload." + extension;
        }
        return basename;
    }

    private String buildStorageFolder() {
        return STORAGE_FOLDER_FORMATTER.format(LocalDate.now()) + "/" + UUID.randomUUID();
    }

    private String extractStorageFolder(String originalStorageKey) {
        Path path = Paths.get(originalStorageKey).normalize();
        Path parent = path.getParent();
        if (parent == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid media storage key.");
        }
        return parent.toString().replace('\\', '/');
    }

    private VariantDimensions computeVariantDimensions(Integer widthPx, Integer heightPx, int maxDimension) {
        if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid image dimensions.");
        }
        double scale = Math.min(1.0d, (double) maxDimension / Math.max(widthPx, heightPx));
        int targetWidth = Math.max(1, (int) Math.round(widthPx * scale));
        int targetHeight = Math.max(1, (int) Math.round(heightPx * scale));
        return new VariantDimensions(targetWidth, targetHeight);
    }

    private String computeSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available.", e);
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void markFailed(MediaAsset asset, String message, Exception exception) {
        if (asset == null || asset.getId() == null) {
            logger.warn("Media upload failed before asset persistence: {}", message, exception);
            return;
        }
        asset.setStatus(STATUS_FAILED);
        asset.setUpdatedAt(OffsetDateTime.now());
        mediaAssetRepository.save(asset);
        logger.warn("Media asset {} marked as FAILED: {}", asset.getId(), message, exception);
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to clean temporary media directory {}", directory, e);
        } catch (UncheckedIOException e) {
            logger.warn("Failed to clean temporary media directory {}", directory, e);
        }
    }

    private record PresetDefinition(String name, int maxDimension) {
    }

    private record VariantDimensions(int widthPx, int heightPx) {
    }

    private record PendingGeneratedVariant(MediaVariant variant, Path file) {
    }
}

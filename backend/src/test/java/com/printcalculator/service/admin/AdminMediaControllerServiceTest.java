package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminCreateMediaUsageRequest;
import com.printcalculator.dto.AdminMediaAssetDto;
import com.printcalculator.dto.AdminMediaUsageDto;
import com.printcalculator.dto.AdminUpdateMediaAssetRequest;
import com.printcalculator.dto.MediaTextTranslationDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminMediaControllerServiceTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private MediaVariantRepository mediaVariantRepository;
    @Mock
    private MediaUsageRepository mediaUsageRepository;
    @Mock
    private MediaImageInspector mediaImageInspector;
    @Mock
    private MediaFfmpegService mediaFfmpegService;
    @Mock
    private ClamAVService clamAVService;

    @TempDir
    Path tempDir;

    private AdminMediaControllerService service;
    private Path storageRoot;

    private final Map<UUID, MediaAsset> assets = new LinkedHashMap<>();
    private final Map<UUID, MediaVariant> variants = new LinkedHashMap<>();
    private final Map<UUID, MediaUsage> usages = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        storageRoot = tempDir.resolve("storage_media");
        MediaStorageService mediaStorageService = new MediaStorageService(
                storageRoot.toString(),
                "https://cdn.example/media"
        );

        service = new AdminMediaControllerService(
                mediaAssetRepository,
                mediaVariantRepository,
                mediaUsageRepository,
                mediaStorageService,
                mediaImageInspector,
                mediaFfmpegService,
                clamAVService,
                1024 * 1024
        );

        when(clamAVService.scan(any())).thenReturn(true);
        when(mediaFfmpegService.canEncode(anyString())).thenReturn(true);

        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            if (asset.getId() == null) {
                asset.setId(UUID.randomUUID());
            }
            assets.put(asset.getId(), asset);
            return asset;
        });
        when(mediaAssetRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(assets.get(invocation.getArgument(0)))
        );
        when(mediaAssetRepository.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation -> assets.values().stream()
                .sorted(Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo)).reversed())
                .toList());

        when(mediaVariantRepository.save(any(MediaVariant.class))).thenAnswer(invocation -> persistVariant(invocation.getArgument(0)));
        when(mediaVariantRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MediaVariant> iterable = invocation.getArgument(0);
            List<MediaVariant> saved = new ArrayList<>();
            for (MediaVariant variant : iterable) {
                saved.add(persistVariant(variant));
            }
            return saved;
        });
        when(mediaVariantRepository.findByMediaAsset_IdIn(anyCollection())).thenAnswer(invocation ->
                variantsForAssets(invocation.getArgument(0))
        );
        when(mediaVariantRepository.findByMediaAsset_IdOrderByCreatedAtAsc(any(UUID.class))).thenAnswer(invocation ->
                variants.values().stream()
                        .filter(variant -> variant.getMediaAsset().getId().equals(invocation.getArgument(0)))
                        .sorted(Comparator.comparing(MediaVariant::getCreatedAt, Comparator.nullsLast(OffsetDateTime::compareTo)))
                        .toList()
        );

        when(mediaUsageRepository.save(any(MediaUsage.class))).thenAnswer(invocation -> persistUsage(invocation.getArgument(0)));
        when(mediaUsageRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MediaUsage> iterable = invocation.getArgument(0);
            List<MediaUsage> saved = new ArrayList<>();
            for (MediaUsage usage : iterable) {
                saved.add(persistUsage(usage));
            }
            return saved;
        });
        when(mediaUsageRepository.findByMediaAsset_IdIn(anyCollection())).thenAnswer(invocation ->
                usagesForAssets(invocation.getArgument(0))
        );
        when(mediaUsageRepository.findByUsageScope(anyString(), anyString(), nullable(UUID.class))).thenAnswer(invocation ->
                usages.values().stream()
                        .filter(usage -> usage.getUsageType().equals(invocation.getArgument(0)))
                        .filter(usage -> usage.getUsageKey().equals(invocation.getArgument(1)))
                        .filter(usage -> {
                            UUID ownerId = invocation.getArgument(2);
                            return ownerId == null ? usage.getOwnerId() == null : ownerId.equals(usage.getOwnerId());
                        })
                        .sorted(Comparator.comparing(MediaUsage::getSortOrder).thenComparing(MediaUsage::getCreatedAt))
                        .toList()
        );
        when(mediaUsageRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(usages.get(invocation.getArgument(0)))
        );

        doAnswer(invocation -> {
            Path outputFile = invocation.getArgument(1);
            String format = invocation.getArgument(4);
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, "generated-" + format, StandardCharsets.UTF_8);
            return null;
        }).when(mediaFfmpegService).generateVariant(any(Path.class), any(Path.class), anyInt(), anyInt(), anyString());
    }

    @Test
    void uploadAsset_withValidImage_shouldPersistMetadataAndExposePublicUrls() throws Exception {
        when(mediaImageInspector.inspect(any(Path.class))).thenReturn(
                new MediaImageInspector.ImageMetadata("image/png", "png", 1600, 900)
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "landing-hero.png",
                "image/png",
                "png-image-content".getBytes(StandardCharsets.UTF_8)
        );

        AdminMediaAssetDto dto = service.uploadAsset(file, " Landing hero ", " Main headline ", null);

        assertEquals("READY", dto.getStatus());
        assertEquals("PUBLIC", dto.getVisibility());
        assertEquals("landing-hero.png", dto.getOriginalFilename());
        assertEquals("Landing hero", dto.getTitle());
        assertEquals("Main headline", dto.getAltText());
        assertEquals("image/png", dto.getMimeType());
        assertEquals(1600, dto.getWidthPx());
        assertEquals(900, dto.getHeightPx());
        assertEquals(file.getSize(), dto.getFileSizeBytes());
        assertEquals(64, dto.getSha256Hex().length());
        assertEquals(10, dto.getVariants().size());

        long publicVariants = dto.getVariants().stream()
                .filter(variant -> !"ORIGINAL".equals(variant.getFormat()))
                .count();
        assertEquals(9, publicVariants);
        assertTrue(dto.getVariants().stream()
                .filter(variant -> "WEBP".equals(variant.getFormat()) && "hero".equals(variant.getVariantName()))
                .allMatch(variant -> variant.getPublicUrl().startsWith("https://cdn.example/media/")));
        assertTrue(dto.getVariants().stream()
                .filter(variant -> "ORIGINAL".equals(variant.getFormat()))
                .allMatch(variant -> variant.getPublicUrl() == null));

        MediaVariant heroWebp = variants.values().stream()
                .filter(variant -> "hero".equals(variant.getVariantName()))
                .filter(variant -> "WEBP".equals(variant.getFormat()))
                .findFirst()
                .orElseThrow();
        assertTrue(Files.exists(storageRoot.resolve("public").resolve(heroWebp.getStorageKey())));

        MediaVariant originalVariant = variants.values().stream()
                .filter(variant -> "ORIGINAL".equals(variant.getFormat()))
                .findFirst()
                .orElseThrow();
        assertTrue(Files.exists(storageRoot.resolve("original").resolve(originalVariant.getStorageKey())));
    }

    @Test
    void uploadAsset_withUnsupportedImageType_shouldReturnBadRequest() throws Exception {
        when(mediaImageInspector.inspect(any(Path.class))).thenReturn(
                new MediaImageInspector.ImageMetadata("image/svg+xml", "svg", 400, 400)
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.svg",
                "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.uploadAsset(file, null, null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(assets.isEmpty());
        assertTrue(variants.isEmpty());
    }

    @Test
    void uploadAsset_withLimitedEncoders_shouldKeepAssetReadyAndExposeOnlySupportedVariants() throws Exception {
        when(mediaImageInspector.inspect(any(Path.class))).thenReturn(
                new MediaImageInspector.ImageMetadata("image/jpeg", "jpg", 1200, 800)
        );
        when(mediaFfmpegService.canEncode("JPEG")).thenReturn(true);
        when(mediaFfmpegService.canEncode("WEBP")).thenReturn(false);
        when(mediaFfmpegService.canEncode("AVIF")).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "capability.jpg",
                "image/jpeg",
                "jpeg-image-content".getBytes(StandardCharsets.UTF_8)
        );

        AdminMediaAssetDto dto = service.uploadAsset(file, "Capability", null, "PUBLIC");

        assertEquals("READY", dto.getStatus());
        assertEquals(4, dto.getVariants().size());
        assertEquals(3, dto.getVariants().stream()
                .filter(variant -> "JPEG".equals(variant.getFormat()))
                .count());
        assertTrue(dto.getVariants().stream()
                .noneMatch(variant -> "WEBP".equals(variant.getFormat()) || "AVIF".equals(variant.getFormat())));
    }

    @Test
    void uploadAsset_whenAvifGenerationFails_shouldKeepAssetReadyAndStoreOtherVariants() throws Exception {
        when(mediaImageInspector.inspect(any(Path.class))).thenReturn(
                new MediaImageInspector.ImageMetadata("image/png", "png", 1600, 900)
        );
        doThrow(new java.io.IOException("FFmpeg failed to generate media variant. Unrecognized option 'still-picture'."))
                .when(mediaFfmpegService)
                .generateVariant(any(Path.class), any(Path.class), anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq("AVIF"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "landing-hero.png",
                "image/png",
                "png-image-content".getBytes(StandardCharsets.UTF_8)
        );

        AdminMediaAssetDto dto = service.uploadAsset(file, " Landing hero ", " Main headline ", null);

        assertEquals("READY", dto.getStatus());
        assertEquals(7, dto.getVariants().size());
        assertTrue(dto.getVariants().stream().noneMatch(variant -> "AVIF".equals(variant.getFormat())));
        assertEquals(3, dto.getVariants().stream().filter(variant -> "JPEG".equals(variant.getFormat())).count());
        assertEquals(3, dto.getVariants().stream().filter(variant -> "WEBP".equals(variant.getFormat())).count());
    }

    @Test
    void uploadAsset_withOversizedFile_shouldFailValidationBeforePersistence() {
        service = new AdminMediaControllerService(
                mediaAssetRepository,
                mediaVariantRepository,
                mediaUsageRepository,
                new MediaStorageService(storageRoot.toString(), "https://cdn.example/media"),
                mediaImageInspector,
                mediaFfmpegService,
                clamAVService,
                4
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.png",
                "image/png",
                "12345".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.uploadAsset(file, null, null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(mediaAssetRepository, mediaVariantRepository, mediaUsageRepository, mediaImageInspector, mediaFfmpegService);
    }

    @Test
    void createUsage_withPrimaryFlag_shouldUnsetExistingPrimaryAndMapUsageOnAsset() {
        MediaAsset asset = persistAsset(seedAsset("PUBLIC"));

        MediaUsage existingPrimary = new MediaUsage();
        existingPrimary.setId(UUID.randomUUID());
        existingPrimary.setUsageType("HOME");
        existingPrimary.setUsageKey("landing");
        existingPrimary.setOwnerId(null);
        existingPrimary.setMediaAsset(asset);
        existingPrimary.setSortOrder(0);
        existingPrimary.setIsPrimary(true);
        existingPrimary.setIsActive(true);
        existingPrimary.setCreatedAt(OffsetDateTime.now().minusDays(1));
        persistUsage(existingPrimary);

        AdminCreateMediaUsageRequest payload = new AdminCreateMediaUsageRequest();
        payload.setUsageType("home");
        payload.setUsageKey("landing");
        payload.setMediaAssetId(asset.getId());
        payload.setSortOrder(5);
        payload.setIsPrimary(true);
        payload.setTranslations(buildTranslations("Landing hero", "Hero home alt"));

        AdminMediaUsageDto created = service.createUsage(payload);

        assertEquals("HOME", created.getUsageType());
        assertEquals("landing", created.getUsageKey());
        assertEquals(asset.getId(), created.getMediaAssetId());
        assertEquals(5, created.getSortOrder());
        assertTrue(created.getIsPrimary());
        assertEquals("Landing hero IT", created.getTranslations().get("it").getTitle());
        assertEquals("Hero home alt EN", created.getTranslations().get("en").getAltText());
        assertFalse(usages.get(existingPrimary.getId()).getIsPrimary());

        AdminMediaAssetDto assetDto = service.getAsset(asset.getId());
        assertEquals(2, assetDto.getUsages().size());
        assertTrue(assetDto.getUsages().stream().anyMatch(usage -> usage.getId().equals(created.getId()) && usage.getIsPrimary()));
    }

    @Test
    void createUsage_withoutAllTranslations_shouldFailValidation() {
        MediaAsset asset = persistAsset(seedAsset("PUBLIC"));

        AdminCreateMediaUsageRequest payload = new AdminCreateMediaUsageRequest();
        payload.setUsageType("home");
        payload.setUsageKey("landing");
        payload.setMediaAssetId(asset.getId());
        payload.setTranslations(new LinkedHashMap<>(Map.of(
                "it", translation("Titolo IT", "Alt IT"),
                "en", translation("Title EN", "Alt EN")
        )));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createUsage(payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("translations must include exactly"));
    }

    @Test
    void updateAsset_withPrivateVisibility_shouldMoveGeneratedFilesAndHidePublicUrls() throws Exception {
        when(mediaImageInspector.inspect(any(Path.class))).thenReturn(
                new MediaImageInspector.ImageMetadata("image/jpeg", "jpg", 1200, 800)
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "gallery.jpg",
                "image/jpeg",
                "jpeg-image-content".getBytes(StandardCharsets.UTF_8)
        );

        AdminMediaAssetDto uploaded = service.uploadAsset(file, null, null, "PUBLIC");
        MediaVariant thumbJpeg = variants.values().stream()
                .filter(variant -> "thumb".equals(variant.getVariantName()))
                .filter(variant -> "JPEG".equals(variant.getFormat()))
                .findFirst()
                .orElseThrow();

        Path publicFile = storageRoot.resolve("public").resolve(thumbJpeg.getStorageKey());
        assertTrue(Files.exists(publicFile));

        AdminUpdateMediaAssetRequest payload = new AdminUpdateMediaAssetRequest();
        payload.setVisibility("PRIVATE");

        AdminMediaAssetDto updated = service.updateAsset(uploaded.getId(), payload);

        assertEquals("PRIVATE", updated.getVisibility());
        assertFalse(Files.exists(publicFile));
        assertTrue(Files.exists(storageRoot.resolve("private").resolve(thumbJpeg.getStorageKey())));
        assertTrue(updated.getVariants().stream()
                .filter(variant -> !"ORIGINAL".equals(variant.getFormat()))
                .allMatch(variant -> variant.getPublicUrl() == null));
    }

    private MediaAsset seedAsset(String visibility) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setOriginalFilename("asset.png");
        asset.setStorageKey("2026/03/" + UUID.randomUUID() + "/original.png");
        asset.setMimeType("image/png");
        asset.setFileSizeBytes(123L);
        asset.setSha256Hex("a".repeat(64));
        asset.setWidthPx(1200);
        asset.setHeightPx(800);
        asset.setStatus("READY");
        asset.setVisibility(visibility);
        asset.setCreatedAt(OffsetDateTime.now());
        asset.setUpdatedAt(OffsetDateTime.now());
        return asset;
    }

    private MediaAsset persistAsset(MediaAsset asset) {
        assets.put(asset.getId(), asset);
        return asset;
    }

    private MediaVariant persistVariant(MediaVariant variant) {
        if (variant.getId() == null) {
            variant.setId(UUID.randomUUID());
        }
        variants.put(variant.getId(), variant);
        return variant;
    }

    private MediaUsage persistUsage(MediaUsage usage) {
        if (usage.getId() == null) {
            usage.setId(UUID.randomUUID());
        }
        usages.put(usage.getId(), usage);
        return usage;
    }

    private Map<String, MediaTextTranslationDto> buildTranslations(String titleBase, String altBase) {
        LinkedHashMap<String, MediaTextTranslationDto> translations = new LinkedHashMap<>();
        translations.put("it", translation(titleBase + " IT", altBase + " IT"));
        translations.put("en", translation(titleBase + " EN", altBase + " EN"));
        translations.put("de", translation(titleBase + " DE", altBase + " DE"));
        translations.put("fr", translation(titleBase + " FR", altBase + " FR"));
        return translations;
    }

    private MediaTextTranslationDto translation(String title, String altText) {
        MediaTextTranslationDto dto = new MediaTextTranslationDto();
        dto.setTitle(title);
        dto.setAltText(altText);
        return dto;
    }

    private List<MediaVariant> variantsForAssets(Collection<UUID> assetIds) {
        return variants.values().stream()
                .filter(variant -> assetIds.contains(variant.getMediaAsset().getId()))
                .toList();
    }

    private List<MediaUsage> usagesForAssets(Collection<UUID> assetIds) {
        return usages.values().stream()
                .filter(usage -> assetIds.contains(usage.getMediaAsset().getId()))
                .toList();
    }
}

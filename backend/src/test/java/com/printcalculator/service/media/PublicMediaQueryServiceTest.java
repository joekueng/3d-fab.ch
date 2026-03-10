package com.printcalculator.service.media;

import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.dto.MediaTextTranslationDto;
import com.printcalculator.entity.MediaAsset;
import com.printcalculator.entity.MediaUsage;
import com.printcalculator.entity.MediaVariant;
import com.printcalculator.repository.MediaUsageRepository;
import com.printcalculator.repository.MediaVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicMediaQueryServiceTest {

    @Mock
    private MediaUsageRepository mediaUsageRepository;
    @Mock
    private MediaVariantRepository mediaVariantRepository;

    @TempDir
    Path tempDir;

    private PublicMediaQueryService service;

    @BeforeEach
    void setUp() {
        MediaStorageService mediaStorageService = new MediaStorageService(
                tempDir.resolve("storage_media").toString(),
                "https://cdn.example/media"
        );
        service = new PublicMediaQueryService(mediaUsageRepository, mediaVariantRepository, mediaStorageService);
    }

    @Test
    void getUsageMedia_shouldReturnOnlyActiveReadyPublicUsagesOrderedBySortOrder() {
        MediaAsset readyPublicAsset = buildAsset("READY", "PUBLIC", "Shop hero fallback", "Shop alt fallback");
        MediaAsset draftAsset = buildAsset("PROCESSING", "PUBLIC", "Draft", "Draft alt");
        MediaAsset privateAsset = buildAsset("READY", "PRIVATE", "Private", "Private alt");

        MediaUsage usageSecond = buildUsage(readyPublicAsset, "HOME_SECTION", "shop-gallery", 2, false, true);
        MediaUsage usageFirst = buildUsage(readyPublicAsset, "HOME_SECTION", "shop-gallery", 1, true, true);
        applyTranslation(usageSecond, "it", "Shop hero IT", "Shop alt IT");
        applyTranslation(usageSecond, "en", "Shop hero EN", "Shop alt EN");
        applyTranslation(usageSecond, "de", "Shop hero DE", "Shop alt DE");
        applyTranslation(usageSecond, "fr", "Shop hero FR", "Shop alt FR");
        applyTranslation(usageFirst, "it", "Shop hero IT", "Shop alt IT");
        applyTranslation(usageFirst, "en", "Shop hero EN", "Shop alt EN");
        applyTranslation(usageFirst, "de", "Shop hero DE", "Shop alt DE");
        applyTranslation(usageFirst, "fr", "Shop hero FR", "Shop alt FR");
        MediaUsage usageDraft = buildUsage(draftAsset, "HOME_SECTION", "shop-gallery", 0, false, true);
        MediaUsage usagePrivate = buildUsage(privateAsset, "HOME_SECTION", "shop-gallery", 3, false, true);

        when(mediaUsageRepository.findActiveByUsageTypeAndUsageKeys(
                "HOME_SECTION", List.of("shop-gallery")
        )).thenReturn(List.of(usageSecond, usageFirst, usageDraft, usagePrivate));
        when(mediaVariantRepository.findByMediaAsset_IdIn(List.of(readyPublicAsset.getId())))
                .thenReturn(List.of(
                        buildVariant(readyPublicAsset, "thumb", "JPEG", "asset/thumb.jpg"),
                        buildVariant(readyPublicAsset, "thumb", "WEBP", "asset/thumb.webp"),
                        buildVariant(readyPublicAsset, "hero", "AVIF", "asset/hero.avif"),
                        buildVariant(readyPublicAsset, "hero", "JPEG", "asset/hero.jpg")
                ));

        List<PublicMediaUsageDto> result = service.getUsageMedia("home_section", "shop-gallery", "en");

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getSortOrder());
        assertEquals(Boolean.TRUE, result.get(0).getIsPrimary());
        assertEquals("Shop hero EN", result.get(0).getTitle());
        assertEquals("Shop alt EN", result.get(0).getAltText());
        assertEquals("https://cdn.example/media/asset/thumb.jpg", result.get(0).getThumb().getJpegUrl());
        assertEquals("https://cdn.example/media/asset/thumb.webp", result.get(0).getThumb().getWebpUrl());
        assertEquals("https://cdn.example/media/asset/hero.avif", result.get(0).getHero().getAvifUrl());
    }

    @Test
    void getUsageMedia_shouldReturnNullForMissingFormatsOrPresetsAndFallbackToAssetMetadata() {
        MediaAsset asset = buildAsset("READY", "PUBLIC", "Joe portrait", "Joe portrait fallback");
        MediaUsage usage = buildUsage(asset, "ABOUT_MEMBER", "joe", 0, true, true);

        when(mediaUsageRepository.findActiveByUsageTypeAndUsageKeys(
                "ABOUT_MEMBER", List.of("joe")
        )).thenReturn(List.of(usage));
        when(mediaVariantRepository.findByMediaAsset_IdIn(List.of(asset.getId())))
                .thenReturn(List.of(buildVariant(asset, "card", "JPEG", "joe/card.jpg")));

        List<PublicMediaUsageDto> result = service.getUsageMedia("ABOUT_MEMBER", "joe", "fr");

        assertEquals(1, result.size());
        assertEquals("Joe portrait", result.get(0).getTitle());
        assertEquals("Joe portrait fallback", result.get(0).getAltText());
        assertNull(result.get(0).getThumb().getJpegUrl());
        assertNull(result.get(0).getCard().getAvifUrl());
        assertEquals("https://cdn.example/media/joe/card.jpg", result.get(0).getCard().getJpegUrl());
        assertNull(result.get(0).getHero().getWebpUrl());
        assertTrue(result.get(0).getIsPrimary());
    }

    private MediaAsset buildAsset(String status, String visibility, String title, String altText) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setStatus(status);
        asset.setVisibility(visibility);
        asset.setTitle(title);
        asset.setAltText(altText);
        asset.setCreatedAt(OffsetDateTime.now());
        asset.setUpdatedAt(OffsetDateTime.now());
        return asset;
    }

    private MediaUsage buildUsage(MediaAsset asset,
                                  String usageType,
                                  String usageKey,
                                  int sortOrder,
                                  boolean isPrimary,
                                  boolean isActive) {
        MediaUsage usage = new MediaUsage();
        usage.setId(UUID.randomUUID());
        usage.setMediaAsset(asset);
        usage.setUsageType(usageType);
        usage.setUsageKey(usageKey);
        usage.setSortOrder(sortOrder);
        usage.setIsPrimary(isPrimary);
        usage.setIsActive(isActive);
        usage.setCreatedAt(OffsetDateTime.now().plusSeconds(sortOrder));
        return usage;
    }

    private MediaVariant buildVariant(MediaAsset asset, String variantName, String format, String storageKey) {
        MediaVariant variant = new MediaVariant();
        variant.setId(UUID.randomUUID());
        variant.setMediaAsset(asset);
        variant.setVariantName(variantName);
        variant.setFormat(format);
        variant.setStorageKey(storageKey);
        variant.setCreatedAt(OffsetDateTime.now());
        return variant;
    }

    private void applyTranslation(MediaUsage usage, String language, String title, String altText) {
        MediaTextTranslationDto translation = new MediaTextTranslationDto();
        translation.setTitle(title);
        translation.setAltText(altText);
        usage.setTitleForLanguage(language, translation.getTitle());
        usage.setAltTextForLanguage(language, translation.getAltText());
    }
}

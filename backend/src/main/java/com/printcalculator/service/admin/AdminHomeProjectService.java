package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminHomeProjectDto;
import com.printcalculator.dto.AdminUpsertHomeProjectRequest;
import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.entity.HomeProject;
import com.printcalculator.entity.MediaUsage;
import com.printcalculator.repository.HomeProjectRepository;
import com.printcalculator.repository.MediaUsageRepository;
import com.printcalculator.service.home.HomeProjectService;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AdminHomeProjectService {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH_PATTERN = Pattern.compile("(^-+|-+$)");
    private static final Safelist HOME_PROJECT_DESCRIPTION_SAFELIST = Safelist.none()
            .addTags("p", "div", "br", "strong", "b", "em", "i", "u", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto", "tel");

    private final HomeProjectRepository homeProjectRepository;
    private final MediaUsageRepository mediaUsageRepository;
    private final PublicMediaQueryService publicMediaQueryService;
    private final AdminMediaControllerService adminMediaControllerService;

    public AdminHomeProjectService(HomeProjectRepository homeProjectRepository,
                                   MediaUsageRepository mediaUsageRepository,
                                   PublicMediaQueryService publicMediaQueryService,
                                   AdminMediaControllerService adminMediaControllerService) {
        this.homeProjectRepository = homeProjectRepository;
        this.mediaUsageRepository = mediaUsageRepository;
        this.publicMediaQueryService = publicMediaQueryService;
        this.adminMediaControllerService = adminMediaControllerService;
    }

    public List<AdminHomeProjectDto> getProjects() {
        return toDtos(homeProjectRepository.findAllByOrderBySortOrderAscCreatedAtAsc());
    }

    public AdminHomeProjectDto getProject(UUID projectId) {
        HomeProject project = homeProjectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home project not found"));
        return toDtos(List.of(project)).get(0);
    }

    @Transactional
    public AdminHomeProjectDto createProject(AdminUpsertHomeProjectRequest payload) {
        ensurePayload(payload);
        LocalizedHomeProjectContent content = normalizeLocalizedContent(payload);
        String slug = normalizeAndValidateSlug(payload.getSlug(), content.defaultTitle());
        ensureSlugAvailable(slug, null);

        HomeProject project = new HomeProject();
        project.setCreatedAt(OffsetDateTime.now());
        applyPayload(project, payload, content, slug);
        HomeProject saved = homeProjectRepository.save(project);
        return getProject(saved.getId());
    }

    @Transactional
    public AdminHomeProjectDto updateProject(UUID projectId, AdminUpsertHomeProjectRequest payload) {
        ensurePayload(payload);
        HomeProject project = homeProjectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home project not found"));

        LocalizedHomeProjectContent content = normalizeLocalizedContent(payload);
        String nextSlug = normalizeAndValidateSlug(payload.getSlug(), content.defaultTitle());
        ensureSlugAvailable(nextSlug, projectId);

        String previousSlug = project.getSlug();
        applyPayload(project, payload, content, nextSlug);
        HomeProject saved = homeProjectRepository.save(project);
        if (previousSlug != null && !previousSlug.equals(nextSlug)) {
            moveProjectMediaUsages(previousSlug, nextSlug);
        }
        return getProject(saved.getId());
    }

    @Transactional
    public void deleteProject(UUID projectId) {
        HomeProject project = homeProjectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home project not found"));
        moveProjectMediaUsages(project.getSlug(), project.getSlug() + "-deleted-" + project.getId());
        homeProjectRepository.delete(project);
    }

    private List<AdminHomeProjectDto> toDtos(List<HomeProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        Map<String, List<PublicMediaUsageDto>> publicImagesByUsageKey = publicMediaQueryService.getUsageMediaMap(
                HomeProjectService.HOME_PROJECT_MEDIA_USAGE_TYPE,
                projects.stream().map(HomeProject::getSlug).toList(),
                null
        );

        return projects.stream()
                .map(project -> {
                    String usageKey = project.getSlug();
                    AdminHomeProjectDto dto = new AdminHomeProjectDto();
                    dto.setId(project.getId());
                    dto.setSlug(project.getSlug());
                    dto.setEyebrowIt(project.getEyebrowIt());
                    dto.setEyebrowEn(project.getEyebrowEn());
                    dto.setEyebrowDe(project.getEyebrowDe());
                    dto.setEyebrowFr(project.getEyebrowFr());
                    dto.setTitleIt(project.getTitleIt());
                    dto.setTitleEn(project.getTitleEn());
                    dto.setTitleDe(project.getTitleDe());
                    dto.setTitleFr(project.getTitleFr());
                    dto.setDescriptionIt(project.getDescriptionIt());
                    dto.setDescriptionEn(project.getDescriptionEn());
                    dto.setDescriptionDe(project.getDescriptionDe());
                    dto.setDescriptionFr(project.getDescriptionFr());
                    dto.setIsActive(project.getIsActive());
                    dto.setSortOrder(project.getSortOrder());
                    dto.setMediaUsageType(HomeProjectService.HOME_PROJECT_MEDIA_USAGE_TYPE);
                    dto.setMediaUsageKey(usageKey);
                    dto.setImages(publicImagesByUsageKey.getOrDefault(usageKey, List.of()));
                    dto.setMediaUsages(adminMediaControllerService.getUsages(
                            HomeProjectService.HOME_PROJECT_MEDIA_USAGE_TYPE,
                            usageKey,
                            null
                    ));
                    dto.setCreatedAt(project.getCreatedAt());
                    dto.setUpdatedAt(project.getUpdatedAt());
                    return dto;
                })
                .toList();
    }

    private void applyPayload(HomeProject project,
                              AdminUpsertHomeProjectRequest payload,
                              LocalizedHomeProjectContent content,
                              String slug) {
        project.setSlug(slug);
        project.setEyebrowIt(content.eyebrows().get("it"));
        project.setEyebrowEn(content.eyebrows().get("en"));
        project.setEyebrowDe(content.eyebrows().get("de"));
        project.setEyebrowFr(content.eyebrows().get("fr"));
        project.setTitleIt(content.titles().get("it"));
        project.setTitleEn(content.titles().get("en"));
        project.setTitleDe(content.titles().get("de"));
        project.setTitleFr(content.titles().get("fr"));
        project.setDescriptionIt(content.descriptions().get("it"));
        project.setDescriptionEn(content.descriptions().get("en"));
        project.setDescriptionDe(content.descriptions().get("de"));
        project.setDescriptionFr(content.descriptions().get("fr"));
        project.setIsActive(payload.getIsActive() == null || payload.getIsActive());
        project.setSortOrder(Math.max(0, payload.getSortOrder() == null ? 0 : payload.getSortOrder()));
    }

    private LocalizedHomeProjectContent normalizeLocalizedContent(AdminUpsertHomeProjectRequest payload) {
        String fallbackTitle = firstNonBlank(
                normalizeOptional(payload.getTitleIt()),
                normalizeOptional(payload.getTitleEn()),
                normalizeOptional(payload.getTitleDe()),
                normalizeOptional(payload.getTitleFr())
        );
        if (fallbackTitle == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project title is required");
        }

        Map<String, String> eyebrows = new LinkedHashMap<>();
        eyebrows.put("it", normalizeOptional(payload.getEyebrowIt()));
        eyebrows.put("en", normalizeOptional(payload.getEyebrowEn()));
        eyebrows.put("de", normalizeOptional(payload.getEyebrowDe()));
        eyebrows.put("fr", normalizeOptional(payload.getEyebrowFr()));

        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("it", normalizeRequired(firstNonBlank(normalizeOptional(payload.getTitleIt()), fallbackTitle), "Italian project title is required"));
        titles.put("en", normalizeRequired(firstNonBlank(normalizeOptional(payload.getTitleEn()), fallbackTitle), "English project title is required"));
        titles.put("de", normalizeRequired(firstNonBlank(normalizeOptional(payload.getTitleDe()), fallbackTitle), "German project title is required"));
        titles.put("fr", normalizeRequired(firstNonBlank(normalizeOptional(payload.getTitleFr()), fallbackTitle), "French project title is required"));

        String fallbackDescription = firstNonBlank(
                normalizeRichTextOptional(payload.getDescriptionIt()),
                normalizeRichTextOptional(payload.getDescriptionEn()),
                normalizeRichTextOptional(payload.getDescriptionDe()),
                normalizeRichTextOptional(payload.getDescriptionFr())
        );
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("it", firstNonBlank(normalizeRichTextOptional(payload.getDescriptionIt()), fallbackDescription));
        descriptions.put("en", firstNonBlank(normalizeRichTextOptional(payload.getDescriptionEn()), fallbackDescription));
        descriptions.put("de", firstNonBlank(normalizeRichTextOptional(payload.getDescriptionDe()), fallbackDescription));
        descriptions.put("fr", firstNonBlank(normalizeRichTextOptional(payload.getDescriptionFr()), fallbackDescription));

        return new LocalizedHomeProjectContent(fallbackTitle, eyebrows, titles, descriptions);
    }

    private void ensureSlugAvailable(String slug, UUID currentProjectId) {
        homeProjectRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (currentProjectId == null || !existing.getId().equals(currentProjectId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Home project slug already exists");
            }
        });
    }

    private void moveProjectMediaUsages(String previousSlug, String nextSlug) {
        List<MediaUsage> mediaUsages = mediaUsageRepository.findByUsageScope(
                HomeProjectService.HOME_PROJECT_MEDIA_USAGE_TYPE,
                previousSlug,
                null
        );
        for (MediaUsage mediaUsage : mediaUsages) {
            mediaUsage.setUsageKey(nextSlug);
        }
        mediaUsageRepository.saveAll(mediaUsages);
    }

    private void ensurePayload(AdminUpsertHomeProjectRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload is required");
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRichTextOptional(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = Jsoup.clean(
                normalized,
                "",
                HOME_PROJECT_DESCRIPTION_SAFELIST,
                new Document.OutputSettings().prettyPrint(false)
        ).trim();

        if (sanitized.isBlank()) {
            return null;
        }

        String plainText = Jsoup.parse(sanitized).text();
        return plainText != null && !plainText.trim().isEmpty() ? sanitized : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeAndValidateSlug(String slug, String fallbackTitle) {
        String source = normalizeOptional(slug);
        if (source == null) {
            source = fallbackTitle;
        }

        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC_PATTERN.matcher(normalized).replaceAll("-");
        normalized = EDGE_DASH_PATTERN.matcher(normalized).replaceAll("");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug is invalid");
        }
        return normalized;
    }

    private record LocalizedHomeProjectContent(
            String defaultTitle,
            Map<String, String> eyebrows,
            Map<String, String> titles,
            Map<String, String> descriptions
    ) {
    }
}

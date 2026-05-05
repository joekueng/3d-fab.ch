package com.printcalculator.service.home;

import com.printcalculator.dto.HomeProjectDto;
import com.printcalculator.dto.PublicMediaUsageDto;
import com.printcalculator.entity.HomeProject;
import com.printcalculator.repository.HomeProjectRepository;
import com.printcalculator.service.media.PublicMediaQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class HomeProjectService {
    public static final String HOME_PROJECT_MEDIA_USAGE_TYPE = "HOME_PROJECT";

    private final HomeProjectRepository homeProjectRepository;
    private final PublicMediaQueryService publicMediaQueryService;

    public HomeProjectService(HomeProjectRepository homeProjectRepository,
                              PublicMediaQueryService publicMediaQueryService) {
        this.homeProjectRepository = homeProjectRepository;
        this.publicMediaQueryService = publicMediaQueryService;
    }

    public List<HomeProjectDto> getActiveProjects(String language) {
        List<HomeProject> projects = homeProjectRepository.findByIsActiveTrueOrderBySortOrderAscCreatedAtAsc();
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<String, List<PublicMediaUsageDto>> mediaByUsageKey = publicMediaQueryService.getUsageMediaMap(
                HOME_PROJECT_MEDIA_USAGE_TYPE,
                projects.stream().map(HomeProject::getSlug).toList(),
                language
        );

        return projects.stream()
                .map(project -> toDto(project, mediaByUsageKey.getOrDefault(project.getSlug(), List.of()), language))
                .toList();
    }

    private HomeProjectDto toDto(HomeProject project, List<PublicMediaUsageDto> images, String language) {
        HomeProjectDto dto = new HomeProjectDto();
        dto.setId(project.getId());
        dto.setSlug(project.getSlug());
        dto.setEyebrow(project.getEyebrowForLanguage(language));
        dto.setTitle(project.getTitleForLanguage(language));
        dto.setDescription(project.getDescriptionForLanguage(language));
        dto.setSortOrder(project.getSortOrder());
        PublicMediaUsageDto primaryImage = pickImage(images);
        dto.setImage(primaryImage);
        dto.setDetailImage(pickDetailImage(images, primaryImage));
        return dto;
    }

    private PublicMediaUsageDto pickImage(List<PublicMediaUsageDto> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                .findFirst()
                .orElse(images.get(0));
    }

    private PublicMediaUsageDto pickDetailImage(List<PublicMediaUsageDto> images, PublicMediaUsageDto primaryImage) {
        if (images == null || images.isEmpty() || primaryImage == null) {
            return null;
        }
        return images.stream()
                .filter(image -> !Objects.equals(image.getMediaAssetId(), primaryImage.getMediaAssetId()))
                .filter(image -> !Boolean.TRUE.equals(image.getIsPrimary()))
                .findFirst()
                .orElse(null);
    }
}

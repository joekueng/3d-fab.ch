package com.printcalculator.dto;

import java.util.UUID;

public class HomeProjectDto {
    private UUID id;
    private String slug;
    private String eyebrow;
    private String title;
    private String description;
    private Integer sortOrder;
    private PublicMediaUsageDto image;
    private PublicMediaUsageDto detailImage;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getEyebrow() {
        return eyebrow;
    }

    public void setEyebrow(String eyebrow) {
        this.eyebrow = eyebrow;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public PublicMediaUsageDto getImage() {
        return image;
    }

    public void setImage(PublicMediaUsageDto image) {
        this.image = image;
    }

    public PublicMediaUsageDto getDetailImage() {
        return detailImage;
    }

    public void setDetailImage(PublicMediaUsageDto detailImage) {
        this.detailImage = detailImage;
    }
}

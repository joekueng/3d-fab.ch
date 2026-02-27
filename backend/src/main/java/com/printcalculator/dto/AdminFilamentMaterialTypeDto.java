package com.printcalculator.dto;

public class AdminFilamentMaterialTypeDto {
    private Long id;
    private String materialCode;
    private Boolean isFlexible;
    private Boolean isTechnical;
    private String technicalTypeLabel;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public Boolean getIsFlexible() {
        return isFlexible;
    }

    public void setIsFlexible(Boolean isFlexible) {
        this.isFlexible = isFlexible;
    }

    public Boolean getIsTechnical() {
        return isTechnical;
    }

    public void setIsTechnical(Boolean isTechnical) {
        this.isTechnical = isTechnical;
    }

    public String getTechnicalTypeLabel() {
        return technicalTypeLabel;
    }

    public void setTechnicalTypeLabel(String technicalTypeLabel) {
        this.technicalTypeLabel = technicalTypeLabel;
    }
}

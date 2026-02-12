package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "filament_material_type")
public class FilamentMaterialType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "filament_material_type_id", nullable = false)
    private Long id;

    @Column(name = "material_code", nullable = false, length = Integer.MAX_VALUE)
    private String materialCode;

    @ColumnDefault("false")
    @Column(name = "is_flexible", nullable = false)
    private Boolean isFlexible;

    @ColumnDefault("false")
    @Column(name = "is_technical", nullable = false)
    private Boolean isTechnical;

    @Column(name = "technical_type_label", length = Integer.MAX_VALUE)
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
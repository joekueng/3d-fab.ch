package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "layer_height_option")
public class LayerHeightOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "layer_height_option_id", nullable = false)
    private Long id;

    @Column(name = "layer_height_mm", nullable = false, precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @ColumnDefault("1.000")
    @Column(name = "time_multiplier", nullable = false, precision = 6, scale = 3)
    private BigDecimal timeMultiplier;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getLayerHeightMm() {
        return layerHeightMm;
    }

    public void setLayerHeightMm(BigDecimal layerHeightMm) {
        this.layerHeightMm = layerHeightMm;
    }

    public BigDecimal getTimeMultiplier() {
        return timeMultiplier;
    }

    public void setTimeMultiplier(BigDecimal timeMultiplier) {
        this.timeMultiplier = timeMultiplier;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

}
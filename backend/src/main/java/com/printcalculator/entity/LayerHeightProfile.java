package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "layer_height_profile")
public class LayerHeightProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "layer_height_profile_id", nullable = false)
    private Long id;

    @Column(name = "profile_name", nullable = false, length = Integer.MAX_VALUE)
    private String profileName;

    @Column(name = "min_layer_height_mm", nullable = false, precision = 5, scale = 3)
    private BigDecimal minLayerHeightMm;

    @Column(name = "max_layer_height_mm", nullable = false, precision = 5, scale = 3)
    private BigDecimal maxLayerHeightMm;

    @Column(name = "default_layer_height_mm", nullable = false, precision = 5, scale = 3)
    private BigDecimal defaultLayerHeightMm;

    @ColumnDefault("1.000")
    @Column(name = "time_multiplier", nullable = false, precision = 6, scale = 3)
    private BigDecimal timeMultiplier;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public BigDecimal getMinLayerHeightMm() {
        return minLayerHeightMm;
    }

    public void setMinLayerHeightMm(BigDecimal minLayerHeightMm) {
        this.minLayerHeightMm = minLayerHeightMm;
    }

    public BigDecimal getMaxLayerHeightMm() {
        return maxLayerHeightMm;
    }

    public void setMaxLayerHeightMm(BigDecimal maxLayerHeightMm) {
        this.maxLayerHeightMm = maxLayerHeightMm;
    }

    public BigDecimal getDefaultLayerHeightMm() {
        return defaultLayerHeightMm;
    }

    public void setDefaultLayerHeightMm(BigDecimal defaultLayerHeightMm) {
        this.defaultLayerHeightMm = defaultLayerHeightMm;
    }

    public BigDecimal getTimeMultiplier() {
        return timeMultiplier;
    }

    public void setTimeMultiplier(BigDecimal timeMultiplier) {
        this.timeMultiplier = timeMultiplier;
    }

}
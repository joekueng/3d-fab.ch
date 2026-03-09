package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(
        name = "nozzle_layer_height_option",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_nozzle_layer_height_option_nozzle_layer",
                columnNames = {"nozzle_diameter_mm", "layer_height_mm"}
        )
)
public class NozzleLayerHeightOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nozzle_layer_height_option_id", nullable = false)
    private Long id;

    @Column(name = "nozzle_diameter_mm", nullable = false, precision = 4, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @Column(name = "layer_height_mm", nullable = false, precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getNozzleDiameterMm() {
        return nozzleDiameterMm;
    }

    public void setNozzleDiameterMm(BigDecimal nozzleDiameterMm) {
        this.nozzleDiameterMm = nozzleDiameterMm;
    }

    public BigDecimal getLayerHeightMm() {
        return layerHeightMm;
    }

    public void setLayerHeightMm(BigDecimal layerHeightMm) {
        this.layerHeightMm = layerHeightMm;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

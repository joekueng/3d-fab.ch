package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "printer_machine")
public class PrinterMachine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "printer_machine_id", nullable = false)
    private Long id;

    @Column(name = "printer_display_name", nullable = false, length = Integer.MAX_VALUE)
    private String printerDisplayName;

    @Column(name = "build_volume_x_mm", nullable = false)
    private Integer buildVolumeXMm;

    @Column(name = "build_volume_y_mm", nullable = false)
    private Integer buildVolumeYMm;

    @Column(name = "build_volume_z_mm", nullable = false)
    private Integer buildVolumeZMm;

    @Column(name = "power_watts", nullable = false)
    private Integer powerWatts;

    @ColumnDefault("1.000")
    @Column(name = "fleet_weight", nullable = false, precision = 6, scale = 3)
    private BigDecimal fleetWeight;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "slicer_machine_profile")
    private String slicerMachineProfile;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPrinterDisplayName() {
        return printerDisplayName;
    }

    public void setPrinterDisplayName(String printerDisplayName) {
        this.printerDisplayName = printerDisplayName;
    }

    public String getSlicerMachineProfile() {
        return slicerMachineProfile;
    }

    public void setSlicerMachineProfile(String slicerMachineProfile) {
        this.slicerMachineProfile = slicerMachineProfile;
    }

    public Integer getBuildVolumeXMm() {
        return buildVolumeXMm;
    }

    public void setBuildVolumeXMm(Integer buildVolumeXMm) {
        this.buildVolumeXMm = buildVolumeXMm;
    }

    public Integer getBuildVolumeYMm() {
        return buildVolumeYMm;
    }

    public void setBuildVolumeYMm(Integer buildVolumeYMm) {
        this.buildVolumeYMm = buildVolumeYMm;
    }

    public Integer getBuildVolumeZMm() {
        return buildVolumeZMm;
    }

    public void setBuildVolumeZMm(Integer buildVolumeZMm) {
        this.buildVolumeZMm = buildVolumeZMm;
    }

    public Integer getPowerWatts() {
        return powerWatts;
    }

    public void setPowerWatts(Integer powerWatts) {
        this.powerWatts = powerWatts;
    }

    public BigDecimal getFleetWeight() {
        return fleetWeight;
    }

    public void setFleetWeight(BigDecimal fleetWeight) {
        this.fleetWeight = fleetWeight;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
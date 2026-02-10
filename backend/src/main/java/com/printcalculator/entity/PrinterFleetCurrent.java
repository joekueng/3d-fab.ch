package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Id;

@Entity
@Immutable
@Table(name = "printer_fleet_current")
public class PrinterFleetCurrent {
    @Id
    @Column(name = "fleet_id")
    private Long id;

    @Column(name = "weighted_average_power_watts")
    private Integer weightedAveragePowerWatts;

    @Column(name = "fleet_max_build_x_mm")
    private Integer fleetMaxBuildXMm;

    @Column(name = "fleet_max_build_y_mm")
    private Integer fleetMaxBuildYMm;

    @Column(name = "fleet_max_build_z_mm")
    private Integer fleetMaxBuildZMm;

    public Integer getWeightedAveragePowerWatts() {
        return weightedAveragePowerWatts;
    }

    public Integer getFleetMaxBuildXMm() {
        return fleetMaxBuildXMm;
    }

    public Integer getFleetMaxBuildYMm() {
        return fleetMaxBuildYMm;
    }

    public Integer getFleetMaxBuildZMm() {
        return fleetMaxBuildZMm;
    }

}
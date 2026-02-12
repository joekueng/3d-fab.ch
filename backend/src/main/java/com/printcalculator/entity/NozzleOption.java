package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "nozzle_option")
public class NozzleOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nozzle_option_id", nullable = false)
    private Long id;

    @Column(name = "nozzle_diameter_mm", nullable = false, precision = 4, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @ColumnDefault("0")
    @Column(name = "owned_quantity", nullable = false)
    private Integer ownedQuantity;

    @ColumnDefault("0.00")
    @Column(name = "extra_nozzle_change_fee_chf", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraNozzleChangeFeeChf;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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

    public Integer getOwnedQuantity() {
        return ownedQuantity;
    }

    public void setOwnedQuantity(Integer ownedQuantity) {
        this.ownedQuantity = ownedQuantity;
    }

    public BigDecimal getExtraNozzleChangeFeeChf() {
        return extraNozzleChangeFeeChf;
    }

    public void setExtraNozzleChangeFeeChf(BigDecimal extraNozzleChangeFeeChf) {
        this.extraNozzleChangeFeeChf = extraNozzleChangeFeeChf;
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
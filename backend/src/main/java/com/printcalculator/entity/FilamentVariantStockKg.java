package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "filament_variant_stock_kg")
public class FilamentVariantStockKg {
    @Id
    @Column(name = "filament_variant_id")
    private Long filamentVariantId;

    @Column(name = "stock_spools", precision = 6, scale = 3)
    private BigDecimal stockSpools;

    @Column(name = "spool_net_kg", precision = 6, scale = 3)
    private BigDecimal spoolNetKg;

    @Column(name = "stock_kg")
    private BigDecimal stockKg;

    public Long getFilamentVariantId() {
        return filamentVariantId;
    }

    public BigDecimal getStockSpools() {
        return stockSpools;
    }

    public BigDecimal getSpoolNetKg() {
        return spoolNetKg;
    }

    public BigDecimal getStockKg() {
        return stockKg;
    }

}
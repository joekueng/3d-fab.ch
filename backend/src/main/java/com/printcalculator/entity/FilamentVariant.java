package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "filament_variant")
public class FilamentVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "filament_variant_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filament_material_type_id", nullable = false)
    private FilamentMaterialType filamentMaterialType;

    @Column(name = "variant_display_name", nullable = false, length = Integer.MAX_VALUE)
    private String variantDisplayName;

    @Column(name = "color_name", nullable = false, length = Integer.MAX_VALUE)
    private String colorName;

    @Column(name = "color_hex", length = Integer.MAX_VALUE)
    private String colorHex;

    @ColumnDefault("'GLOSSY'")
    @Column(name = "finish_type", length = Integer.MAX_VALUE)
    private String finishType;

    @Column(name = "brand", length = Integer.MAX_VALUE)
    private String brand;

    @ColumnDefault("false")
    @Column(name = "is_matte", nullable = false)
    private Boolean isMatte;

    @ColumnDefault("false")
    @Column(name = "is_special", nullable = false)
    private Boolean isSpecial;

    @Column(name = "cost_chf_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal costChfPerKg;

    @ColumnDefault("0.000")
    @Column(name = "stock_spools", nullable = false, precision = 6, scale = 3)
    private BigDecimal stockSpools;

    @ColumnDefault("1.000")
    @Column(name = "spool_net_kg", nullable = false, precision = 6, scale = 3)
    private BigDecimal spoolNetKg;

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

    public FilamentMaterialType getFilamentMaterialType() {
        return filamentMaterialType;
    }

    public void setFilamentMaterialType(FilamentMaterialType filamentMaterialType) {
        this.filamentMaterialType = filamentMaterialType;
    }

    public String getVariantDisplayName() {
        return variantDisplayName;
    }

    public void setVariantDisplayName(String variantDisplayName) {
        this.variantDisplayName = variantDisplayName;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getFinishType() {
        return finishType;
    }

    public void setFinishType(String finishType) {
        this.finishType = finishType;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Boolean getIsMatte() {
        return isMatte;
    }

    public void setIsMatte(Boolean isMatte) {
        this.isMatte = isMatte;
    }

    public Boolean getIsSpecial() {
        return isSpecial;
    }

    public void setIsSpecial(Boolean isSpecial) {
        this.isSpecial = isSpecial;
    }

    public BigDecimal getCostChfPerKg() {
        return costChfPerKg;
    }

    public void setCostChfPerKg(BigDecimal costChfPerKg) {
        this.costChfPerKg = costChfPerKg;
    }

    public BigDecimal getStockSpools() {
        return stockSpools;
    }

    public void setStockSpools(BigDecimal stockSpools) {
        this.stockSpools = stockSpools;
    }

    public BigDecimal getSpoolNetKg() {
        return spoolNetKg;
    }

    public void setSpoolNetKg(BigDecimal spoolNetKg) {
        this.spoolNetKg = spoolNetKg;
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

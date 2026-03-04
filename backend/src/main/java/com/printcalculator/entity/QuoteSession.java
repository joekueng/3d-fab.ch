package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quote_sessions", indexes = {
        @Index(name = "ix_quote_sessions_status",
                columnList = "status"),
        @Index(name = "ix_quote_sessions_expires_at",
                columnList = "expires_at")})
public class QuoteSession {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "quote_session_id", nullable = false)
    private UUID id;

    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "pricing_version", nullable = false, length = Integer.MAX_VALUE)
    private String pricingVersion;

    @Column(name = "material_code", nullable = false, length = Integer.MAX_VALUE)
    private String materialCode;

    @Column(name = "nozzle_diameter_mm", precision = 5, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @Column(name = "layer_height_mm", precision = 6, scale = 3)
    private BigDecimal layerHeightMm;

    @Column(name = "infill_pattern", length = Integer.MAX_VALUE)
    private String infillPattern;

    @Column(name = "infill_percent")
    private Integer infillPercent;

    @ColumnDefault("false")
    @Column(name = "supports_enabled", nullable = false)
    private Boolean supportsEnabled;

    @Column(name = "notes", length = Integer.MAX_VALUE)
    private String notes;

    @ColumnDefault("0.00")
    @Column(name = "setup_cost_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal setupCostChf;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "converted_order_id")
    private UUID convertedOrderId;

    @Column(name = "source_request_id")
    private UUID sourceRequestId;

    @Column(name = "cad_hours", precision = 10, scale = 2)
    private BigDecimal cadHours;

    @Column(name = "cad_hourly_rate_chf", precision = 10, scale = 2)
    private BigDecimal cadHourlyRateChf;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPricingVersion() {
        return pricingVersion;
    }

    public void setPricingVersion(String pricingVersion) {
        this.pricingVersion = pricingVersion;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
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

    public String getInfillPattern() {
        return infillPattern;
    }

    public void setInfillPattern(String infillPattern) {
        this.infillPattern = infillPattern;
    }

    public Integer getInfillPercent() {
        return infillPercent;
    }

    public void setInfillPercent(Integer infillPercent) {
        this.infillPercent = infillPercent;
    }

    public Boolean getSupportsEnabled() {
        return supportsEnabled;
    }

    public void setSupportsEnabled(Boolean supportsEnabled) {
        this.supportsEnabled = supportsEnabled;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSetupCostChf() {
        return setupCostChf;
    }

    public void setSetupCostChf(BigDecimal setupCostChf) {
        this.setupCostChf = setupCostChf;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getConvertedOrderId() {
        return convertedOrderId;
    }

    public void setConvertedOrderId(UUID convertedOrderId) {
        this.convertedOrderId = convertedOrderId;
    }

    public UUID getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(UUID sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public BigDecimal getCadHours() {
        return cadHours;
    }

    public void setCadHours(BigDecimal cadHours) {
        this.cadHours = cadHours;
    }

    public BigDecimal getCadHourlyRateChf() {
        return cadHourlyRateChf;
    }

    public void setCadHourlyRateChf(BigDecimal cadHourlyRateChf) {
        this.cadHourlyRateChf = cadHourlyRateChf;
    }

}

package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quote_line_items", indexes = {@Index(name = "ix_quote_line_items_session",
        columnList = "quote_session_id")})
public class QuoteLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "quote_line_item_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "quote_session_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private QuoteSession quoteSession;

    @Column(name = "status", nullable = false, length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "original_filename", nullable = false, length = Integer.MAX_VALUE)
    private String originalFilename;

    @ColumnDefault("1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "color_code", length = Integer.MAX_VALUE)
    private String colorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filament_variant_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private FilamentVariant filamentVariant;

    @Column(name = "bounding_box_x_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxXMm;

    @Column(name = "bounding_box_y_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxYMm;

    @Column(name = "bounding_box_z_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxZMm;

    @Column(name = "print_time_seconds")
    private Integer printTimeSeconds;

    @Column(name = "material_grams", precision = 12, scale = 2)
    private BigDecimal materialGrams;

    @Column(name = "unit_price_chf", precision = 12, scale = 2)
    private BigDecimal unitPriceChf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_breakdown")
    private Map<String, Object> pricingBreakdown;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @Column(name = "stored_path", length = Integer.MAX_VALUE)
    private String storedPath;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QuoteSession getQuoteSession() {
        return quoteSession;
    }

    public void setQuoteSession(QuoteSession quoteSession) {
        this.quoteSession = quoteSession;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public FilamentVariant getFilamentVariant() {
        return filamentVariant;
    }

    public void setFilamentVariant(FilamentVariant filamentVariant) {
        this.filamentVariant = filamentVariant;
    }

    public BigDecimal getBoundingBoxXMm() {
        return boundingBoxXMm;
    }

    public void setBoundingBoxXMm(BigDecimal boundingBoxXMm) {
        this.boundingBoxXMm = boundingBoxXMm;
    }

    public BigDecimal getBoundingBoxYMm() {
        return boundingBoxYMm;
    }

    public void setBoundingBoxYMm(BigDecimal boundingBoxYMm) {
        this.boundingBoxYMm = boundingBoxYMm;
    }

    public BigDecimal getBoundingBoxZMm() {
        return boundingBoxZMm;
    }

    public void setBoundingBoxZMm(BigDecimal boundingBoxZMm) {
        this.boundingBoxZMm = boundingBoxZMm;
    }

    public Integer getPrintTimeSeconds() {
        return printTimeSeconds;
    }

    public void setPrintTimeSeconds(Integer printTimeSeconds) {
        this.printTimeSeconds = printTimeSeconds;
    }

    public BigDecimal getMaterialGrams() {
        return materialGrams;
    }

    public void setMaterialGrams(BigDecimal materialGrams) {
        this.materialGrams = materialGrams;
    }

    public BigDecimal getUnitPriceChf() {
        return unitPriceChf;
    }

    public void setUnitPriceChf(BigDecimal unitPriceChf) {
        this.unitPriceChf = unitPriceChf;
    }

    public Map<String, Object> getPricingBreakdown() {
        return pricingBreakdown;
    }

    public void setPricingBreakdown(Map<String, Object> pricingBreakdown) {
        this.pricingBreakdown = pricingBreakdown;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}

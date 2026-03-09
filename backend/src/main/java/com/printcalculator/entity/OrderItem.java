package com.printcalculator.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items", indexes = {@Index(name = "ix_order_items_order",
        columnList = "order_id")})
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "order_item_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "original_filename", nullable = false, length = Integer.MAX_VALUE)
    private String originalFilename;

    @Column(name = "stored_relative_path", nullable = false, length = Integer.MAX_VALUE)
    private String storedRelativePath;

    @Column(name = "stored_filename", nullable = false, length = Integer.MAX_VALUE)
    private String storedFilename;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = Integer.MAX_VALUE)
    private String mimeType;

    @Column(name = "sha256_hex", length = Integer.MAX_VALUE)
    private String sha256Hex;

    @Column(name = "material_code", nullable = false, length = Integer.MAX_VALUE)
    private String materialCode;

    @Column(name = "quality", length = Integer.MAX_VALUE)
    private String quality;

    @Column(name = "nozzle_diameter_mm", precision = 4, scale = 2)
    private BigDecimal nozzleDiameterMm;

    @Column(name = "layer_height_mm", precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @Column(name = "infill_percent")
    private Integer infillPercent;

    @Column(name = "infill_pattern", length = Integer.MAX_VALUE)
    private String infillPattern;

    @Column(name = "supports_enabled")
    private Boolean supportsEnabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filament_variant_id")
    private FilamentVariant filamentVariant;

    @Column(name = "color_code", length = Integer.MAX_VALUE)
    private String colorCode;

    @ColumnDefault("1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "print_time_seconds")
    private Integer printTimeSeconds;

    @Column(name = "material_grams", precision = 12, scale = 2)
    private BigDecimal materialGrams;

    @Column(name = "bounding_box_x_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxXMm;

    @Column(name = "bounding_box_y_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxYMm;

    @Column(name = "bounding_box_z_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxZMm;

    @Column(name = "unit_price_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceChf;

    @Column(name = "line_total_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotalChf;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (quantity == null) {
            quantity = 1;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredRelativePath() {
        return storedRelativePath;
    }

    public void setStoredRelativePath(String storedRelativePath) {
        this.storedRelativePath = storedRelativePath;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
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

    public Integer getInfillPercent() {
        return infillPercent;
    }

    public void setInfillPercent(Integer infillPercent) {
        this.infillPercent = infillPercent;
    }

    public String getInfillPattern() {
        return infillPattern;
    }

    public void setInfillPattern(String infillPattern) {
        this.infillPattern = infillPattern;
    }

    public Boolean getSupportsEnabled() {
        return supportsEnabled;
    }

    public void setSupportsEnabled(Boolean supportsEnabled) {
        this.supportsEnabled = supportsEnabled;
    }

    public FilamentVariant getFilamentVariant() {
        return filamentVariant;
    }

    public void setFilamentVariant(FilamentVariant filamentVariant) {
        this.filamentVariant = filamentVariant;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
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

    public BigDecimal getUnitPriceChf() {
        return unitPriceChf;
    }

    public void setUnitPriceChf(BigDecimal unitPriceChf) {
        this.unitPriceChf = unitPriceChf;
    }

    public BigDecimal getLineTotalChf() {
        return lineTotalChf;
    }

    public void setLineTotalChf(BigDecimal lineTotalChf) {
        this.lineTotalChf = lineTotalChf;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

}

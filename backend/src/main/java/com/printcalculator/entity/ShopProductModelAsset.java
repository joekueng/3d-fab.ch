package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shop_product_model_asset", indexes = {
        @Index(name = "ix_shop_product_model_asset_product", columnList = "shop_product_id")
})
public class ShopProductModelAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "shop_product_model_asset_id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_product_id", nullable = false, unique = true)
    private ShopProduct product;

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

    @Column(name = "bounding_box_x_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxXMm;

    @Column(name = "bounding_box_y_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxYMm;

    @Column(name = "bounding_box_z_mm", precision = 10, scale = 3)
    private BigDecimal boundingBoxZMm;

    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ShopProduct getProduct() {
        return product;
    }

    public void setProduct(ShopProduct product) {
        this.product = product;
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

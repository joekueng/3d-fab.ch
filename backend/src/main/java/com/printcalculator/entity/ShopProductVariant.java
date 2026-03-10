package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shop_product_variant", indexes = {
        @Index(name = "ix_shop_product_variant_product_active_sort", columnList = "shop_product_id, is_active, sort_order"),
        @Index(name = "ix_shop_product_variant_sku", columnList = "sku")
})
public class ShopProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "shop_product_variant_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_product_id", nullable = false)
    private ShopProduct product;

    @Column(name = "sku", unique = true, length = Integer.MAX_VALUE)
    private String sku;

    @Column(name = "variant_label", nullable = false, length = Integer.MAX_VALUE)
    private String variantLabel;

    @Column(name = "color_name", nullable = false, length = Integer.MAX_VALUE)
    private String colorName;

    @Column(name = "color_hex", length = Integer.MAX_VALUE)
    private String colorHex;

    @Column(name = "internal_material_code", nullable = false, length = Integer.MAX_VALUE)
    private String internalMaterialCode;

    @ColumnDefault("0.00")
    @Column(name = "price_chf", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceChf;

    @ColumnDefault("false")
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

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
        if (priceChf == null) {
            priceChf = BigDecimal.ZERO;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if (priceChf == null) {
            priceChf = BigDecimal.ZERO;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public void setVariantLabel(String variantLabel) {
        this.variantLabel = variantLabel;
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

    public String getInternalMaterialCode() {
        return internalMaterialCode;
    }

    public void setInternalMaterialCode(String internalMaterialCode) {
        this.internalMaterialCode = internalMaterialCode;
    }

    public BigDecimal getPriceChf() {
        return priceChf;
    }

    public void setPriceChf(BigDecimal priceChf) {
        this.priceChf = priceChf;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

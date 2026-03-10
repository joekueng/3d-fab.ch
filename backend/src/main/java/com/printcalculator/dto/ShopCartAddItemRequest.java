package com.printcalculator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ShopCartAddItemRequest {
    @NotNull
    private UUID shopProductVariantId;

    @Min(1)
    private Integer quantity = 1;

    public UUID getShopProductVariantId() {
        return shopProductVariantId;
    }

    public void setShopProductVariantId(UUID shopProductVariantId) {
        this.shopProductVariantId = shopProductVariantId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

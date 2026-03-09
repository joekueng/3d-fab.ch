package com.printcalculator.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ShopCartUpdateItemRequest {
    @NotNull
    @Min(1)
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}

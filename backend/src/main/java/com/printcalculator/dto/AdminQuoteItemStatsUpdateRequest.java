package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminQuoteItemStatsUpdateRequest {
    private boolean persist;

    @NotEmpty
    @Valid
    private List<Item> items = new ArrayList<>();

    public boolean isPersist() {
        return persist;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        @NotNull
        private UUID itemId;

        @NotNull
        @Min(1)
        private Integer printTimeSeconds;

        @NotNull
        @Positive
        private BigDecimal materialGrams;

        public UUID getItemId() {
            return itemId;
        }

        public void setItemId(UUID itemId) {
            this.itemId = itemId;
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
    }
}

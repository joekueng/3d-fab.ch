package com.printcalculator.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminPageDto<T>(List<T> items, int page, int size, long totalElements) {
    public static <T> AdminPageDto<T> from(Page<T> page) {
        return new AdminPageDto<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}

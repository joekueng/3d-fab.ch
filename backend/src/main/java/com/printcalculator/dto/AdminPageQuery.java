package com.printcalculator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public record AdminPageQuery(@Min(0) Integer page, @Min(1) @Max(100) Integer size) {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    public Pageable toPageable() {
        return PageRequest.of(page == null ? 0 : page, size == null ? 25 : size, NEWEST_FIRST);
    }
}

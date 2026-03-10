package com.printcalculator.repository;

import com.printcalculator.entity.ShopCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopCategoryRepository extends JpaRepository<ShopCategory, UUID> {
    Optional<ShopCategory> findBySlug(String slug);

    Optional<ShopCategory> findBySlugIgnoreCase(String slug);

    Optional<ShopCategory> findBySlugAndIsActiveTrue(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByParentCategory_Id(UUID parentCategoryId);

    List<ShopCategory> findAllByOrderBySortOrderAscNameAsc();

    List<ShopCategory> findAllByIsActiveTrueOrderBySortOrderAscNameAsc();
}

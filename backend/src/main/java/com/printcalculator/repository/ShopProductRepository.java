package com.printcalculator.repository;

import com.printcalculator.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopProductRepository extends JpaRepository<ShopProduct, UUID> {
    Optional<ShopProduct> findBySlug(String slug);

    Optional<ShopProduct> findBySlugIgnoreCase(String slug);

    Optional<ShopProduct> findBySlugAndIsActiveTrue(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    List<ShopProduct> findAllByOrderBySortOrderAscNameAsc();

    List<ShopProduct> findAllByOrderByIsFeaturedDescSortOrderAscNameAsc();

    List<ShopProduct> findByCategory_IdOrderBySortOrderAscNameAsc(UUID categoryId);

    List<ShopProduct> findAllByIsActiveTrueOrderByIsFeaturedDescSortOrderAscNameAsc();

    boolean existsByCategory_Id(UUID categoryId);
}

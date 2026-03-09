package com.printcalculator.repository;

import com.printcalculator.entity.ShopProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopProductVariantRepository extends JpaRepository<ShopProductVariant, UUID> {
    List<ShopProductVariant> findByProduct_IdOrderBySortOrderAscColorNameAsc(UUID productId);

    Optional<ShopProductVariant> findFirstByProduct_IdAndIsDefaultTrue(UUID productId);

    boolean existsBySkuIgnoreCase(String sku);
}

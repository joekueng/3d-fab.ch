package com.printcalculator.repository;

import com.printcalculator.entity.ShopProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopProductVariantRepository extends JpaRepository<ShopProductVariant, UUID> {
    @Modifying
    @Query("""
            update ShopProductVariant variant
            set variant.colorLabelEn = :en, variant.colorLabelDe = :de, variant.colorLabelFr = :fr
            where variant.id = :id and coalesce(variant.colorLabelIt, '') = :italian
              and variant.colorName = :colorName
            """)
    int updateColorTranslations(@Param("id") UUID id, @Param("italian") String italian,
                                @Param("colorName") String colorName, @Param("en") String en,
                                @Param("de") String de, @Param("fr") String fr);

    List<ShopProductVariant> findByProduct_IdOrderBySortOrderAscColorNameAsc(UUID productId);

    List<ShopProductVariant> findByProduct_IdInOrderBySortOrderAscColorNameAsc(Collection<UUID> productIds);

    List<ShopProductVariant> findByProduct_IdAndIsActiveTrueOrderBySortOrderAscColorNameAsc(UUID productId);

    List<ShopProductVariant> findByProduct_IdInAndIsActiveTrueOrderBySortOrderAscColorNameAsc(Collection<UUID> productIds);

    Optional<ShopProductVariant> findFirstByProduct_IdAndIsDefaultTrue(UUID productId);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID variantId);
}

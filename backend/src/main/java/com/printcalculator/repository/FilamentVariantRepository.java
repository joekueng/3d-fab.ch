package com.printcalculator.repository;

import com.printcalculator.entity.FilamentVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.printcalculator.entity.FilamentMaterialType;
import java.util.List;
import java.util.Optional;

public interface FilamentVariantRepository extends JpaRepository<FilamentVariant, Long> {
    @Modifying
    @Query("""
            update FilamentVariant variant
            set variant.colorLabelEn = :en, variant.colorLabelDe = :de, variant.colorLabelFr = :fr
            where variant.id = :id and coalesce(variant.colorLabelIt, '') = :italian
              and variant.colorName = :colorName
            """)
    int updateColorTranslations(@Param("id") Long id, @Param("italian") String italian,
                                @Param("colorName") String colorName, @Param("en") String en,
                                @Param("de") String de, @Param("fr") String fr);

    @EntityGraph(attributePaths = {"filamentMaterialType"})
    List<FilamentVariant> findByIsActiveTrue();

    // We try to match by color name if possible, or get first active
    Optional<FilamentVariant> findByFilamentMaterialTypeAndColorName(FilamentMaterialType type, String colorName);
    Optional<FilamentVariant> findByFilamentMaterialTypeAndVariantDisplayName(FilamentMaterialType type, String variantDisplayName);
    Optional<FilamentVariant> findFirstByFilamentMaterialTypeAndIsActiveTrue(FilamentMaterialType type);
}

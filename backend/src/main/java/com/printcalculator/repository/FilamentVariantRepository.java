package com.printcalculator.repository;

import com.printcalculator.entity.FilamentVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import com.printcalculator.entity.FilamentMaterialType;
import java.util.Optional;

public interface FilamentVariantRepository extends JpaRepository<FilamentVariant, Long> {
    // We try to match by color name if possible, or get first active
    Optional<FilamentVariant> findByFilamentMaterialTypeAndColorName(FilamentMaterialType type, String colorName);
    Optional<FilamentVariant> findByFilamentMaterialTypeAndVariantDisplayName(FilamentMaterialType type, String variantDisplayName);
    Optional<FilamentVariant> findFirstByFilamentMaterialTypeAndIsActiveTrue(FilamentMaterialType type);
}

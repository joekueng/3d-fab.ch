package com.printcalculator.repository;

import com.printcalculator.entity.FilamentMaterialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilamentMaterialTypeRepository extends JpaRepository<FilamentMaterialType, Long> {
    Optional<FilamentMaterialType> findByMaterialCode(String materialCode);
}
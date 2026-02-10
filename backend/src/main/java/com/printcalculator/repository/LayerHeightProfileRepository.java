package com.printcalculator.repository;

import com.printcalculator.entity.LayerHeightProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayerHeightProfileRepository extends JpaRepository<LayerHeightProfile, Long> {
}
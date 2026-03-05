package com.printcalculator.repository;

import com.printcalculator.entity.NozzleLayerHeightOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NozzleLayerHeightOptionRepository extends JpaRepository<NozzleLayerHeightOption, Long> {
    List<NozzleLayerHeightOption> findByIsActiveTrueOrderByNozzleDiameterMmAscLayerHeightMmAsc();
}

package com.printcalculator.repository;

import com.printcalculator.entity.NozzleOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface NozzleOptionRepository extends JpaRepository<NozzleOption, Long> {
    Optional<NozzleOption> findFirstByNozzleDiameterMmAndIsActiveTrue(BigDecimal nozzleDiameterMm);
}

package com.printcalculator.repository;

import com.printcalculator.entity.InfillPattern;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfillPatternRepository extends JpaRepository<InfillPattern, Long> {
}
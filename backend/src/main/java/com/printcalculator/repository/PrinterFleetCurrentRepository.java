package com.printcalculator.repository;

import com.printcalculator.entity.PrinterFleetCurrent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterFleetCurrentRepository extends JpaRepository<PrinterFleetCurrent, Long> {
}
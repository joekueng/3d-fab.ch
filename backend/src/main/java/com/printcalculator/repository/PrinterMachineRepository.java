package com.printcalculator.repository;

import com.printcalculator.entity.PrinterMachine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrinterMachineRepository extends JpaRepository<PrinterMachine, Long> {
    Optional<PrinterMachine> findByPrinterDisplayName(String printerDisplayName);
    Optional<PrinterMachine> findFirstByIsActiveTrueOrderByIdAsc();
    List<PrinterMachine> findByIsActiveTrueOrderByIdAsc();
}

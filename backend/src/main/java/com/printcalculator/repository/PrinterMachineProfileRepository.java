package com.printcalculator.repository;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.PrinterMachineProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PrinterMachineProfileRepository extends JpaRepository<PrinterMachineProfile, Long> {
    Optional<PrinterMachineProfile> findByPrinterMachineAndNozzleDiameterMmAndIsActiveTrue(PrinterMachine printerMachine, BigDecimal nozzleDiameterMm);
    Optional<PrinterMachineProfile> findFirstByPrinterMachineAndIsDefaultTrueAndIsActiveTrue(PrinterMachine printerMachine);
    List<PrinterMachineProfile> findByPrinterMachineAndIsActiveTrue(PrinterMachine printerMachine);
}

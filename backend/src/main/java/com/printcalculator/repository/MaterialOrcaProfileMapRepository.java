package com.printcalculator.repository;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.MaterialOrcaProfileMap;
import com.printcalculator.entity.PrinterMachineProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialOrcaProfileMapRepository extends JpaRepository<MaterialOrcaProfileMap, Long> {
    Optional<MaterialOrcaProfileMap> findByPrinterMachineProfileAndFilamentMaterialTypeAndIsActiveTrue(
            PrinterMachineProfile printerMachineProfile,
            FilamentMaterialType filamentMaterialType
    );

    List<MaterialOrcaProfileMap> findByPrinterMachineProfileAndIsActiveTrue(PrinterMachineProfile printerMachineProfile);
}

package com.printcalculator.repository;

import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.FilamentVariantOrcaOverride;
import com.printcalculator.entity.PrinterMachineProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FilamentVariantOrcaOverrideRepository extends JpaRepository<FilamentVariantOrcaOverride, Long> {
    Optional<FilamentVariantOrcaOverride> findByFilamentVariantAndPrinterMachineProfileAndIsActiveTrue(
            FilamentVariant filamentVariant,
            PrinterMachineProfile printerMachineProfile
    );
}

package com.printcalculator.service;

import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.FilamentVariantOrcaOverride;
import com.printcalculator.entity.MaterialOrcaProfileMap;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.PrinterMachineProfile;
import com.printcalculator.repository.FilamentVariantOrcaOverrideRepository;
import com.printcalculator.repository.MaterialOrcaProfileMapRepository;
import com.printcalculator.repository.PrinterMachineProfileRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class OrcaProfileResolver {

    private final PrinterMachineProfileRepository machineProfileRepo;
    private final MaterialOrcaProfileMapRepository materialMapRepo;
    private final FilamentVariantOrcaOverrideRepository variantOverrideRepo;

    public OrcaProfileResolver(
            PrinterMachineProfileRepository machineProfileRepo,
            MaterialOrcaProfileMapRepository materialMapRepo,
            FilamentVariantOrcaOverrideRepository variantOverrideRepo
    ) {
        this.machineProfileRepo = machineProfileRepo;
        this.materialMapRepo = materialMapRepo;
        this.variantOverrideRepo = variantOverrideRepo;
    }

    public ResolvedProfiles resolve(PrinterMachine printerMachine, BigDecimal nozzleDiameterMm, FilamentVariant variant) {
        Optional<PrinterMachineProfile> machineProfileOpt = resolveMachineProfile(printerMachine, nozzleDiameterMm);

        String machineProfileName = machineProfileOpt
                .map(PrinterMachineProfile::getOrcaMachineProfileName)
                .orElseGet(() -> fallbackMachineProfile(printerMachine, nozzleDiameterMm));

        String filamentProfileName = machineProfileOpt
                .map(machineProfile -> resolveFilamentProfileWithMachineProfile(machineProfile, variant)
                        .orElseGet(() -> fallbackFilamentProfile(variant.getFilamentMaterialType())))
                .orElseGet(() -> fallbackFilamentProfile(variant.getFilamentMaterialType()));

        return new ResolvedProfiles(machineProfileName, filamentProfileName, machineProfileOpt.orElse(null));
    }

    public Optional<PrinterMachineProfile> resolveMachineProfile(PrinterMachine machine, BigDecimal nozzleDiameterMm) {
        if (machine == null) {
            return Optional.empty();
        }

        BigDecimal normalizedNozzle = normalizeNozzle(nozzleDiameterMm);
        if (normalizedNozzle != null) {
            Optional<PrinterMachineProfile> exact = machineProfileRepo
                    .findByPrinterMachineAndNozzleDiameterMmAndIsActiveTrue(machine, normalizedNozzle);
            if (exact.isPresent()) {
                return exact;
            }
        }

        Optional<PrinterMachineProfile> defaultProfile = machineProfileRepo
                .findFirstByPrinterMachineAndIsDefaultTrueAndIsActiveTrue(machine);
        if (defaultProfile.isPresent()) {
            return defaultProfile;
        }

        return machineProfileRepo.findByPrinterMachineAndIsActiveTrue(machine)
                .stream()
                .findFirst();
    }

    private Optional<String> resolveFilamentProfileWithMachineProfile(PrinterMachineProfile machineProfile, FilamentVariant variant) {
        if (machineProfile == null || variant == null) {
            return Optional.empty();
        }

        Optional<FilamentVariantOrcaOverride> override = variantOverrideRepo
                .findByFilamentVariantAndPrinterMachineProfileAndIsActiveTrue(variant, machineProfile);

        if (override.isPresent()) {
            return Optional.ofNullable(override.get().getOrcaFilamentProfileName());
        }

        Optional<MaterialOrcaProfileMap> map = materialMapRepo
                .findByPrinterMachineProfileAndFilamentMaterialTypeAndIsActiveTrue(
                        machineProfile,
                        variant.getFilamentMaterialType()
                );

        return map.map(MaterialOrcaProfileMap::getOrcaFilamentProfileName);
    }

    private String fallbackMachineProfile(PrinterMachine machine, BigDecimal nozzleDiameterMm) {
        if (machine == null || machine.getPrinterDisplayName() == null || machine.getPrinterDisplayName().isBlank()) {
            return "Bambu Lab A1 0.4 nozzle";
        }

        String displayName = machine.getPrinterDisplayName();
        if (displayName.toLowerCase().contains("bambulab a1") || displayName.toLowerCase().contains("bambu lab a1")) {
            String nozzleForProfile = formatNozzleForProfileName(nozzleDiameterMm);
            if (nozzleForProfile == null) {
                return "Bambu Lab A1 0.4 nozzle";
            }
            return "Bambu Lab A1 " + nozzleForProfile + " nozzle";
        }

        return displayName;
    }

    private String fallbackFilamentProfile(FilamentMaterialType materialType) {
        String materialCode = materialType != null && materialType.getMaterialCode() != null
                ? materialType.getMaterialCode().trim().toUpperCase()
                : "PLA";

        return switch (materialCode) {
            case "PLA TOUGH" -> "Bambu PLA Tough @BBL A1";
            case "PETG" -> "Generic PETG";
            case "TPU" -> "Generic TPU";
            case "PC" -> "Generic PC";
            case "ABS" -> "Generic ABS";
            default -> "Generic PLA";
        };
    }

    private BigDecimal normalizeNozzle(BigDecimal nozzleDiameterMm) {
        if (nozzleDiameterMm == null) {
            return null;
        }
        return nozzleDiameterMm.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatNozzleForProfileName(BigDecimal nozzleDiameterMm) {
        BigDecimal normalizedNozzle = normalizeNozzle(nozzleDiameterMm);
        if (normalizedNozzle == null) {
            return null;
        }
        BigDecimal stripped = normalizedNozzle.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    public record ResolvedProfiles(
            String machineProfileName,
            String filamentProfileName,
            PrinterMachineProfile machineProfile
    ) {}
}

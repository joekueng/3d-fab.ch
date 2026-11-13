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

    public static boolean requiresAsaSupports(FilamentVariant variant, boolean supportsEnabled) {
        String material = variant != null && variant.getFilamentMaterialType() != null
                ? variant.getFilamentMaterialType().getMaterialCode() : null;
        return supportsEnabled && material != null && java.util.Set.of("PA12CF", "PETCF")
                .contains(material.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s_-]", ""));
    }

    public static boolean isX2dProfile(String profileName) {
        return profileName != null && profileName.startsWith("Bambu Lab X2D ") && profileName.endsWith(" nozzle");
    }

    private static String x2dFilamentProfile(String material, String machineProfileName) {
        return "Generic " + material + " @BBL X2D"
                + (machineProfileName.equals("Bambu Lab X2D 0.4 nozzle") ? " 0.4 nozzle" : "");
    }

    public ResolvedProfiles resolve(PrinterMachine printerMachine, BigDecimal nozzleDiameterMm, FilamentVariant variant) {
        Optional<PrinterMachineProfile> machineProfileOpt = resolveMachineProfile(printerMachine, nozzleDiameterMm);

        String machineProfileName = machineProfileOpt
                .map(PrinterMachineProfile::getOrcaMachineProfileName)
                .orElseGet(() -> fallbackMachineProfile(printerMachine, nozzleDiameterMm));

        String filamentProfileName = machineProfileOpt
                .map(machineProfile -> resolveFilamentProfileWithMachineProfile(machineProfile, variant)
                        .orElseGet(() -> fallbackFilamentProfile(variant.getFilamentMaterialType(), machineProfileName)))
                .orElseGet(() -> fallbackFilamentProfile(variant.getFilamentMaterialType(), machineProfileName));

        return new ResolvedProfiles(machineProfileName, filamentProfileName, machineProfileOpt.orElse(null));
    }

    public Optional<String> resolveAsaSupportProfile(ResolvedProfiles profiles, FilamentVariant variant,
                                                     boolean supportsEnabled) {
        if (!requiresAsaSupports(variant, supportsEnabled)) return Optional.empty();
        // Reuse the configured ASA profile for this printer/nozzle when available.
        // ASA is the only automatic support material policy, and only for PA12-CF and PET-CF.
        if (profiles.machineProfile() != null) {
            Optional<String> mapped = materialMapRepo
                    .findByPrinterMachineProfileAndIsActiveTrue(profiles.machineProfile()).stream()
                    .filter(map -> "ASA".equalsIgnoreCase(map.getFilamentMaterialType().getMaterialCode()))
                    .map(MaterialOrcaProfileMap::getOrcaFilamentProfileName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst();
            if (mapped.isPresent()) return mapped;
        }
        return Optional.of(isX2dProfile(profiles.machineProfileName())
                ? x2dFilamentProfile("ASA", profiles.machineProfileName()) : "Generic ASA");
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

        if (displayName.toLowerCase().contains("bambulab p2s") || displayName.toLowerCase().contains("bambu lab p2s")) {
            String nozzleForProfile = formatNozzleForProfileName(nozzleDiameterMm);
            if (nozzleForProfile == null) {
                return "Bambu Lab P2S 0.4 nozzle";
            }
            return "Bambu Lab P2S " + nozzleForProfile + " nozzle";
        }

        if (displayName.toLowerCase(java.util.Locale.ROOT).replace(" ", "").contains("bambulabx2d")) {
            String nozzle = formatNozzleForProfileName(nozzleDiameterMm);
            return "Bambu Lab X2D " + (nozzle == null ? "0.4" : nozzle) + " nozzle";
        }

        return displayName;
    }

    private String fallbackFilamentProfile(FilamentMaterialType materialType, String machineProfileName) {
        String materialCode = materialType != null && materialType.getMaterialCode() != null
                ? materialType.getMaterialCode().trim().toUpperCase()
                : "PLA";

        String normalized = materialCode.replaceAll("[\\s_-]", "");
        if ("PCABS".equals(normalized)) {
            if (machineProfileName.startsWith("Bambu Lab P2S ")) return "Polymaker PC-ABS @BBL P2S";
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "PC-ABS requires its configured P2S profile");
        }
        if ("TPU85A".equals(normalized)) {
            if ("Bambu Lab P2S 0.6 nozzle".equals(machineProfileName)) return "Bambu TPU 85A @BBL P2S";
            if ("Bambu Lab P2S 0.8 nozzle".equals(machineProfileName)) return "Bambu TPU 85A @BBL P2S 0.8 nozzle";
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "TPU 85A requires its configured P2S 0.6 mm or 0.8 mm profile");
        }
        if (isX2dProfile(machineProfileName) && "PA12CF".equals(normalized)) {
            return x2dFilamentProfile("PA-CF", machineProfileName);
        }
        if ("PETCF".equals(normalized)) {
            return isX2dProfile(machineProfileName)
                    ? "Bambu PET-CF @BBL X2D" + (machineProfileName.contains("0.4 nozzle") ? " 0.4 nozzle" : "")
                    : "Bambu PET-CF @BBL X1C";
        }

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

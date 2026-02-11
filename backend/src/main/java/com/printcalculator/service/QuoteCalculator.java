package com.printcalculator.service;


import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.PricingPolicyMachineHourTier;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PricingPolicyMachineHourTierRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class QuoteCalculator {

    private final PricingPolicyRepository pricingRepo;
    private final PricingPolicyMachineHourTierRepository tierRepo;
    private final PrinterMachineRepository machineRepo;
    private final FilamentMaterialTypeRepository materialRepo;
    private final FilamentVariantRepository variantRepo;

    public QuoteCalculator(PricingPolicyRepository pricingRepo,
                           PricingPolicyMachineHourTierRepository tierRepo,
                           PrinterMachineRepository machineRepo,
                           FilamentMaterialTypeRepository materialRepo,
                           FilamentVariantRepository variantRepo) {
        this.pricingRepo = pricingRepo;
        this.tierRepo = tierRepo;
        this.machineRepo = machineRepo;
        this.materialRepo = materialRepo;
        this.variantRepo = variantRepo;
    }

    public QuoteResult calculate(PrintStats stats, String machineName, String filamentProfileName) {
        // 1. Fetch Active Policy
        PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        if (policy == null) {
            throw new RuntimeException("No active pricing policy found");
        }

        // 2. Fetch Machine Info
        // Map "bambu_a1" -> "BambuLab A1" or similar?
        // Ideally we should use the display name from DB.
        // For now, if machineName is a code, we might need a mapping or just fuzzy search.
        // Let's assume machineName is mapped or we search by display name.
        // If not found, fallback to first active.
        PrinterMachine machine = machineRepo.findByPrinterDisplayName(machineName).orElse(null);
        if (machine == null) {
             // Try "BambuLab A1" if code was "bambu_a1" logic or just get first active
             machine = machineRepo.findFirstByIsActiveTrue()
                 .orElseThrow(() -> new RuntimeException("No active printer found"));
        }

        // 3. Fetch Filament Info
        // filamentProfileName might be "bambu_pla_basic_black" or "Generic PLA"
        // We try to extract material code (PLA, PETG)
        String materialCode = detectMaterialCode(filamentProfileName);
        FilamentMaterialType materialType = materialRepo.findByMaterialCode(materialCode)
                .orElseThrow(() -> new RuntimeException("Unknown material type: " + materialCode));

        // Try to find specific variant (e.g. by color if we could parse it)
        // For now, get default/first active variant for this material
        FilamentVariant variant = variantRepo.findFirstByFilamentMaterialTypeAndIsActiveTrue(materialType)
                .orElseThrow(() -> new RuntimeException("No active variant for material: " + materialCode));


        // --- CALCULATIONS ---

        // Material Cost: (weight / 1000) * costPerKg
        BigDecimal weightKg = BigDecimal.valueOf(stats.filamentWeightGrams()).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal materialCost = weightKg.multiply(variant.getCostChfPerKg());

        // Machine Cost: Tiered
        BigDecimal totalHours = BigDecimal.valueOf(stats.printTimeSeconds()).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        BigDecimal machineCost = calculateMachineCost(policy, totalHours);

        // Energy Cost: (watts / 1000) * hours * costPerKwh
        BigDecimal kw = BigDecimal.valueOf(machine.getPowerWatts()).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal kwh = kw.multiply(totalHours);
        BigDecimal energyCost = kwh.multiply(policy.getElectricityCostChfPerKwh());

        // Subtotal (Costs + Fixed Fees)
        BigDecimal fixedFee = policy.getFixedJobFeeChf();
        BigDecimal subtotal = materialCost.add(machineCost).add(energyCost).add(fixedFee);

        // Markup
        // Markup is percentage (e.g. 20.0)
        BigDecimal markupFactor = BigDecimal.ONE.add(policy.getMarkupPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal totalPrice = subtotal.multiply(markupFactor).setScale(2, RoundingMode.HALF_UP);

        return new QuoteResult(totalPrice.doubleValue(), "CHF", stats, fixedFee.doubleValue());
    }

    private BigDecimal calculateMachineCost(PricingPolicy policy, BigDecimal hours) {
        List<PricingPolicyMachineHourTier> tiers = tierRepo.findAllByPricingPolicyOrderByTierStartHoursAsc(policy);
        if (tiers.isEmpty()) {
            return BigDecimal.ZERO; // Should not happen if DB is correct
        }

        BigDecimal remainingHours = hours;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal processedHours = BigDecimal.ZERO;

        for (PricingPolicyMachineHourTier tier : tiers) {
            if (remainingHours.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal tierStart = tier.getTierStartHours();
            BigDecimal tierEnd = tier.getTierEndHours(); // can be null for infinity
            
            // Determine duration in this tier
            // Valid duration in this tier = (min(tierEnd, totalHours) - tierStart)
            // But logic is simpler: we consume hours sequentially?
            // "0-10h @ 2CHF, 10-20h @ 1.5CHF" implies:
            // 5h job -> 5 * 2
            // 15h job -> 10 * 2 + 5 * 1.5
            
            BigDecimal tierDuration;
            
            // Max hours applicable in this tier relative to 0
            BigDecimal tierLimit = (tierEnd != null) ? tierEnd : BigDecimal.valueOf(Long.MAX_VALUE);
            
            // The amount of hours falling into this bucket
            // Upper bound for this calculation is min(totalHours, tierLimit)
            // Lower bound is tierStart
            // So hours in this bucket = max(0, min(totalHours, tierLimit) - tierStart)
            
            BigDecimal upper = hours.min(tierLimit);
            BigDecimal lower = tierStart;
            
            if (upper.compareTo(lower) > 0) {
                 BigDecimal hoursInTier = upper.subtract(lower);
                 totalCost = totalCost.add(hoursInTier.multiply(tier.getMachineCostChfPerHour()));
            }
        }
        
        return totalCost;
    }

    private String detectMaterialCode(String profileName) {
        String lower = profileName.toLowerCase();
        if (lower.contains("petg")) return "PETG";
        if (lower.contains("tpu")) return "TPU";
        if (lower.contains("abs")) return "ABS";
        if (lower.contains("nylon")) return "Nylon";
        if (lower.contains("asa")) return "ASA";
        // Default to PLA
        return "PLA";
    }
}

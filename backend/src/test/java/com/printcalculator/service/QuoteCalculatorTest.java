package com.printcalculator.service;

import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PricingPolicyMachineHourTierRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteCalculatorTest {
    private PricingPolicyRepository pricingRepo;
    private PrinterMachineRepository machineRepo;
    private QuoteCalculator calculator;

    @BeforeEach
    void setUp() {
        pricingRepo = mock(PricingPolicyRepository.class);
        machineRepo = mock(PrinterMachineRepository.class);
        calculator = new QuoteCalculator(
                pricingRepo,
                mock(PricingPolicyMachineHourTierRepository.class),
                machineRepo,
                mock(FilamentMaterialTypeRepository.class),
                mock(FilamentVariantRepository.class)
        );
    }

    @Test
    void calculatePrintItemUnitPrice_appliesMaterialEnergyAndMarkup() {
        BigDecimal price = calculator.calculatePrintItemUnitPrice(
                1234,
                new BigDecimal("123.45"),
                machine(250),
                policy("0.25", "20"),
                variant("20.00")
        );

        assertAmountEquals("2.98971", price);
    }

    @Test
    void calculate_usesTheSameFormulaForSlicingStats() {
        PricingPolicy policy = policy("0.30", "20");
        PrinterMachine machine = machine(1000);
        FilamentVariant variant = variant("25.00");
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findByPrinterDisplayName("BambuLab A1")).thenReturn(Optional.of(machine));

        PrintStats stats = new PrintStats(3600, "1h", 1000.0, 1000.0);
        QuoteResult result = calculator.calculate(stats, "BambuLab A1", variant);

        assertAmountEquals("30.36", BigDecimal.valueOf(result.getTotalPrice()));
        assertEquals("CHF", result.getCurrency());
    }

    private PricingPolicy policy(String electricityCostChfPerKwh, String markupPercent) {
        PricingPolicy policy = new PricingPolicy();
        policy.setElectricityCostChfPerKwh(new BigDecimal(electricityCostChfPerKwh));
        policy.setMarkupPercent(new BigDecimal(markupPercent));
        return policy;
    }

    private PrinterMachine machine(int powerWatts) {
        PrinterMachine machine = new PrinterMachine();
        machine.setPowerWatts(powerWatts);
        return machine;
    }

    private FilamentVariant variant(String costChfPerKg) {
        FilamentVariant variant = new FilamentVariant();
        variant.setCostChfPerKg(new BigDecimal(costChfPerKg));
        return variant;
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }
}

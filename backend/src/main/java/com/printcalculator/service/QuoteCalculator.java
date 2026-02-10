package com.printcalculator.service;

import com.printcalculator.config.AppProperties;
import com.printcalculator.model.CostBreakdown;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuoteCalculator {

    private final AppProperties props;

    public QuoteCalculator(AppProperties props) {
        this.props = props;
    }

    public QuoteResult calculate(PrintStats stats) {
        // Material Cost: (weight / 1000) * costPerKg
        BigDecimal weightKg = BigDecimal.valueOf(stats.filamentWeightGrams()).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal materialCost = weightKg.multiply(BigDecimal.valueOf(props.getFilamentCostPerKg()));

        // Machine Cost: (seconds / 3600) * costPerHour
        BigDecimal hours = BigDecimal.valueOf(stats.printTimeSeconds()).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        BigDecimal machineCost = hours.multiply(BigDecimal.valueOf(props.getMachineCostPerHour()));

        // Energy Cost: (watts / 1000) * hours * costPerKwh
        BigDecimal kw = BigDecimal.valueOf(props.getPrinterPowerWatts()).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal kwh = kw.multiply(hours);
        BigDecimal energyCost = kwh.multiply(BigDecimal.valueOf(props.getEnergyCostPerKwh()));

        // Subtotal
        BigDecimal subtotal = materialCost.add(machineCost).add(energyCost);

        // Markup
        BigDecimal markupFactor = BigDecimal.valueOf(1.0 + (props.getMarkupPercent() / 100.0));
        BigDecimal totalPrice = subtotal.multiply(markupFactor).setScale(2, RoundingMode.HALF_UP);

        BigDecimal markupAmount = totalPrice.subtract(subtotal);

        CostBreakdown breakdown = new CostBreakdown(
            materialCost.setScale(2, RoundingMode.HALF_UP),
            machineCost.setScale(2, RoundingMode.HALF_UP),
            energyCost.setScale(2, RoundingMode.HALF_UP),
            subtotal.setScale(2, RoundingMode.HALF_UP),
            markupAmount.setScale(2, RoundingMode.HALF_UP)
        );
        
        List<String> notes = new ArrayList<>();
        // notes.add("Generated via Dynamic Slicer (Java Backend)");

        return new QuoteResult(totalPrice, "CHF", stats, breakdown, notes);
    }
}

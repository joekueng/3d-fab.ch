package com.printcalculator.service;

import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.PricingPolicyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
public class QuoteSessionTotalsService {
    private final PricingPolicyRepository pricingRepo;
    private final QuoteCalculator quoteCalculator;

    public QuoteSessionTotalsService(PricingPolicyRepository pricingRepo, QuoteCalculator quoteCalculator) {
        this.pricingRepo = pricingRepo;
        this.quoteCalculator = quoteCalculator;
    }

    public QuoteSessionTotals compute(QuoteSession session, List<QuoteLineItem> items) {
        BigDecimal printItemsBaseTotal = BigDecimal.ZERO;
        BigDecimal totalSeconds = BigDecimal.ZERO;

        for (QuoteLineItem item : items) {
            int quantity = normalizeQuantity(item.getQuantity());
            BigDecimal unitPrice = item.getUnitPriceChf() != null ? item.getUnitPriceChf() : BigDecimal.ZERO;
            printItemsBaseTotal = printItemsBaseTotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));

            if (item.getPrintTimeSeconds() != null && item.getPrintTimeSeconds() > 0) {
                totalSeconds = totalSeconds.add(BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(quantity)));
            }
        }

        BigDecimal totalHours = totalSeconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        BigDecimal globalMachineCost = quoteCalculator.calculateSessionMachineCost(policy, totalHours);
        BigDecimal printItemsTotal = printItemsBaseTotal.add(globalMachineCost);

        BigDecimal cadTotal = calculateCadTotal(session);
        BigDecimal itemsTotal = printItemsTotal.add(cadTotal);

        BigDecimal setupFee = session.getSetupCostChf() != null ? session.getSetupCostChf() : BigDecimal.ZERO;
        BigDecimal shippingCost = calculateShippingCost(items);
        BigDecimal grandTotal = itemsTotal.add(setupFee).add(shippingCost);

        return new QuoteSessionTotals(
                printItemsTotal,
                globalMachineCost,
                cadTotal,
                itemsTotal,
                setupFee,
                shippingCost,
                grandTotal,
                totalSeconds
        );
    }

    public BigDecimal calculateCadTotal(QuoteSession session) {
        if (session == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cadHours = session.getCadHours() != null ? session.getCadHours() : BigDecimal.ZERO;
        BigDecimal cadRate = session.getCadHourlyRateChf() != null ? session.getCadHourlyRateChf() : BigDecimal.ZERO;
        if (cadHours.compareTo(BigDecimal.ZERO) <= 0 || cadRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return cadHours.multiply(cadRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateShippingCost(List<QuoteLineItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        boolean exceedsBaseSize = false;
        for (QuoteLineItem item : items) {
            BigDecimal x = item.getBoundingBoxXMm() != null ? item.getBoundingBoxXMm() : BigDecimal.ZERO;
            BigDecimal y = item.getBoundingBoxYMm() != null ? item.getBoundingBoxYMm() : BigDecimal.ZERO;
            BigDecimal z = item.getBoundingBoxZMm() != null ? item.getBoundingBoxZMm() : BigDecimal.ZERO;

            BigDecimal[] dims = {x, y, z};
            Arrays.sort(dims);

            if (dims[2].compareTo(BigDecimal.valueOf(250.0)) > 0
                    || dims[1].compareTo(BigDecimal.valueOf(176.0)) > 0
                    || dims[0].compareTo(BigDecimal.valueOf(20.0)) > 0) {
                exceedsBaseSize = true;
                break;
            }
        }

        int totalQuantity = items.stream().mapToInt(i -> normalizeQuantity(i.getQuantity())).sum();
        if (totalQuantity <= 0) {
            return BigDecimal.ZERO;
        }

        if (exceedsBaseSize) {
            return totalQuantity > 5 ? BigDecimal.valueOf(9.00) : BigDecimal.valueOf(4.00);
        }
        return BigDecimal.valueOf(2.00);
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            return 1;
        }
        return quantity;
    }

    public record QuoteSessionTotals(
            BigDecimal printItemsTotalChf,
            BigDecimal globalMachineCostChf,
            BigDecimal cadTotalChf,
            BigDecimal itemsTotalChf,
            BigDecimal setupCostChf,
            BigDecimal shippingCostChf,
            BigDecimal grandTotalChf,
            BigDecimal totalPrintSeconds
    ) {}
}

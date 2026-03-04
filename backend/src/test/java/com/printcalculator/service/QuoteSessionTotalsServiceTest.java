package com.printcalculator.service;

import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.PricingPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteSessionTotalsServiceTest {
    private PricingPolicyRepository pricingRepo;
    private QuoteCalculator quoteCalculator;
    private QuoteSessionTotalsService service;

    @BeforeEach
    void setUp() {
        pricingRepo = mock(PricingPolicyRepository.class);
        quoteCalculator = mock(QuoteCalculator.class);
        service = new QuoteSessionTotalsService(pricingRepo, quoteCalculator);
    }

    @Test
    void compute_WithCadOnlySession_ShouldIncludeCadAndNoShipping() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setCadHours(BigDecimal.valueOf(2));
        session.setCadHourlyRateChf(BigDecimal.valueOf(75));

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(eq(policy), any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of());

        assertAmountEquals("150.00", totals.cadTotalChf());
        assertAmountEquals("0.00", totals.shippingCostChf());
        assertAmountEquals("150.00", totals.itemsTotalChf());
        assertAmountEquals("150.00", totals.grandTotalChf());
    }

    @Test
    void compute_WithPrintItemAndCad_ShouldSumEverything() {
        QuoteSession session = new QuoteSession();
        session.setSetupCostChf(new BigDecimal("5.00"));
        session.setCadHours(new BigDecimal("1.50"));
        session.setCadHourlyRateChf(new BigDecimal("60.00"));

        QuoteLineItem item = new QuoteLineItem();
        item.setQuantity(2);
        item.setUnitPriceChf(new BigDecimal("10.00"));
        item.setPrintTimeSeconds(3600);
        item.setBoundingBoxXMm(new BigDecimal("10"));
        item.setBoundingBoxYMm(new BigDecimal("10"));
        item.setBoundingBoxZMm(new BigDecimal("10"));

        PricingPolicy policy = new PricingPolicy();
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteCalculator.calculateSessionMachineCost(policy, new BigDecimal("2.0000")))
                .thenReturn(new BigDecimal("3.00"));

        QuoteSessionTotalsService.QuoteSessionTotals totals = service.compute(session, List.of(item));

        assertAmountEquals("23.00", totals.printItemsTotalChf());
        assertAmountEquals("90.00", totals.cadTotalChf());
        assertAmountEquals("113.00", totals.itemsTotalChf());
        assertAmountEquals("2.00", totals.shippingCostChf());
        assertAmountEquals("120.00", totals.grandTotalChf());
    }

    private void assertAmountEquals(String expected, BigDecimal actual) {
        assertTrue(new BigDecimal(expected).compareTo(actual) == 0,
                "Expected " + expected + " but got " + actual);
    }
}

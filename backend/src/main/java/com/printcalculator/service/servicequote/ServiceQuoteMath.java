package com.printcalculator.service.servicequote;

import com.printcalculator.dto.ServiceQuoteContent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public final class ServiceQuoteMath {
    private static final BigDecimal LIMIT = new BigDecimal("9999999999.99");
    private ServiceQuoteMath() {}
    public record Totals(BigDecimal estimate, BigDecimal maximum) {}
    public static Totals calculate(ServiceQuoteContent c) {
        BigDecimal amount;
        if (c.pricingMode() == ServiceQuoteContent.PricingMode.HOURLY_ESTIMATE) {
            if (c.estimatedHours() == null || c.hourlyRateChf() == null || c.lumpSumChf() != null)
                throw new ResponseStatusException(BAD_REQUEST, "QUOTE_PRICING_FIELDS");
            amount = c.estimatedHours().multiply(c.hourlyRateChf());
        } else {
            if (c.lumpSumChf() == null || c.estimatedHours() != null || c.hourlyRateChf() != null)
                throw new ResponseStatusException(BAD_REQUEST, "QUOTE_PRICING_FIELDS");
            amount = c.lumpSumChf();
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = c.variationType() == ServiceQuoteContent.VariationType.PERCENTAGE
            ? amount.multiply(c.variationValue()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
            : c.variationValue();
        BigDecimal maximum = amount.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0 || c.variationValue().signum() < 0 || maximum.compareTo(LIMIT) > 0)
            throw new ResponseStatusException(BAD_REQUEST, "QUOTE_AMOUNT_LIMIT");
        return new Totals(amount, maximum);
    }
}

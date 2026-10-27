package com.printcalculator.service.servicequote;

import com.printcalculator.dto.ServiceQuoteContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ServiceQuoteMathTest {
    @ParameterizedTest @CsvSource({"HOURLY_ESTIMATE,PERCENTAGE,15,800.00,920.00", "HOURLY_ESTIMATE,AMOUNT_CHF,100,800.00,900.00", "LUMP_SUM_ESTIMATE,PERCENTAGE,10,500.00,550.00", "LUMP_SUM_ESTIMATE,AMOUNT_CHF,0,500.00,500.00"})
    void exactTotalsForBothEstimateAndMarginModes(String mode,String variation,String value,String estimated,String maximum) {
        boolean hourly=mode.equals("HOURLY_ESTIMATE");
        var content=new ServiceQuoteContent("Design","One service",ServiceQuoteContent.PricingMode.valueOf(mode),hourly?new BigDecimal("10"):null,hourly?new BigDecimal("80"):null,hourly?null:new BigDecimal("500"),ServiceQuoteContent.VariationType.valueOf(variation),new BigDecimal(value),OffsetDateTime.now().plusDays(1),"it",null);
        var totals=ServiceQuoteMath.calculate(content);assertEquals(new BigDecimal(estimated),totals.estimate());assertEquals(new BigDecimal(maximum),totals.maximum());
    }
    @Test void roundsEstimateBeforePercentageAndRejectsOverflow() {
        var c=new ServiceQuoteContent("Design","One service",ServiceQuoteContent.PricingMode.HOURLY_ESTIMATE,new BigDecimal("1.23"),new BigDecimal("1.23"),null,ServiceQuoteContent.VariationType.PERCENTAGE,new BigDecimal("10"),OffsetDateTime.now(),"it",null);
        var totals=ServiceQuoteMath.calculate(c);assertEquals(new BigDecimal("1.51"),totals.estimate());assertEquals(new BigDecimal("1.66"),totals.maximum());
        var limit=new ServiceQuoteContent("Design","One service",ServiceQuoteContent.PricingMode.LUMP_SUM_ESTIMATE,null,null,new BigDecimal("9999999999.99"),ServiceQuoteContent.VariationType.AMOUNT_CHF,BigDecimal.ONE,OffsetDateTime.now(),"it",null);
        assertThrows(ResponseStatusException.class,()->ServiceQuoteMath.calculate(limit));
    }
}

package com.printcalculator.service;

import com.printcalculator.tools.QuoteTimeCurve;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuoteTimeCurveTest {
    @Test
    void interpolatesModelSecondsAndUsesBoundedExtrapolationWithoutAddingStartupTwice() {
        var curve=new QuoteTimeCurve(368,List.of(new QuoteTimeCurve.Knot(5,8),new QuoteTimeCurve.Knot(10,12)));
        assertEquals(1000,curve.modelSeconds(750,100));
        assertEquals(560,curve.modelSeconds(200,100));
        assertEquals(2400,curve.modelSeconds(2000,100));
        long previous=0;
        for(long seconds=1;seconds<3000;seconds++) {
            long value=curve.modelSeconds(seconds,100);
            assertTrue(value>=previous);previous=value;
        }
    }
    @Test
    void rejectsInvalidOrNonmonotoneCurves() {
        assertThrows(IllegalArgumentException.class,()->new QuoteTimeCurve(-1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new QuoteTimeCurve(0,List.of(new QuoteTimeCurve.Knot(1,2),new QuoteTimeCurve.Knot(1,3))));
        assertThrows(IllegalArgumentException.class,()->new QuoteTimeCurve(0,List.of(new QuoteTimeCurve.Knot(1,3),new QuoteTimeCurve.Knot(2,2))));
    }
}

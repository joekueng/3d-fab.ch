package com.printcalculator.service;

import com.printcalculator.tools.QuoteTimeNormalizationService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.tools.CalibrationPrintStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteTimeNormalizationServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final QuoteTimeNormalizationService service=new QuoteTimeNormalizationService(mapper,true);
    private static final String MACHINE="Bambu Lab P2S 0.4 nozzle";
    private static final String PLA="Bambu PLA Basic @BBL P2S";
    private static final BigDecimal NOZZLE=new BigDecimal("0.40"),LAYER=new BigDecimal("0.200");

    @Test
    void preservesNativeStatisticsAndSelectsTheMeasuredReferenceRule() {
        var raw=new CalibrationPrintStats(546,"9m 6s",3.63,1200,533L,100);
        var result=service.normalize(raw,MACHINE,PLA,"PLA",NOZZLE,LAYER,"0.20mm Standard @BBL P2S","standard");
        assertTrue(result.quoteStats().printTimeSeconds()>1100);
        assertTrue(result.quoteStats().printTimeSeconds()<1250);
        assertEquals(3.63,result.quoteStats().filamentWeightGrams());
        assertEquals(1200,result.quoteStats().filamentLengthMm());
        assertSame(raw,result.nativeStats());
        assertEquals("p2s-a1-2026-10-20-v2",result.calibrationVersion());
        assertEquals("514cb2bc314d8fe9",result.ruleId());
        assertEquals("Bambu Lab A1",result.reference());
        assertTrue(result.factor().compareTo(BigDecimal.ONE)>0);
    }

    @Test
    void preservesNativeTimeForUnknownProfilesInconsistentSettingsAndMissingMetadata() {
        var raw=new CalibrationPrintStats(546,"9m 6s",3.63,1200,533L,100);
        assertSame(raw,service.normalize(raw,"Bambu Lab A1 0.4 nozzle",PLA,"PLA",NOZZLE,LAYER).quoteStats());
        assertSame(raw,service.normalize(raw,MACHINE,PLA,"ABS",NOZZLE,LAYER).quoteStats());
        assertSame(raw,service.normalize(raw,MACHINE,"Generic PLA @BBL P2S","PLA",NOZZLE,LAYER).quoteStats());
        assertSame(raw,service.normalize(raw,MACHINE,PLA,"PLA",new BigDecimal("0.6"),LAYER).quoteStats());
        assertSame(raw,service.normalize(raw,MACHINE,PLA,"PLA",NOZZLE,new BigDecimal("0.12"),
                "0.20mm Standard @BBL P2S","standard").quoteStats());
        assertSame(raw,new QuoteTimeNormalizationService(mapper,false).normalize(raw,MACHINE,PLA,"PLA",NOZZLE,LAYER).quoteStats());
        var legacy=new CalibrationPrintStats(546,"9m 6s",3.63,1200);
        assertSame(legacy,service.normalize(legacy,MACHINE,PLA,"PLA",NOZZLE,LAYER).quoteStats());
        var incomplete=new CalibrationPrintStats(546,"9m 6s",3.63,1200,533L,null);
        assertSame(incomplete,service.normalize(incomplete,MACHINE,PLA,"PLA",NOZZLE,LAYER).quoteStats());
    }

    @Test
    void verifiesBothReferenceAndTargetProfilesAndRejectsDriftUnderTheSameName() throws Exception {
        var raw=new CalibrationPrintStats(546,"",3.63,1200,533L,100);
        var profiles=spy(new ProfileManager("profiles",mapper));
        var live=new QuoteTimeNormalizationService(mapper,true,profiles);
        assertNotSame(raw,live.normalize(raw,MACHINE,PLA,"PLA",NOZZLE,LAYER).quoteStats());
        var changed=profiles.getMergedProfile("Bambu Lab A1 0.4 nozzle","machine");
        changed.put("printable_height","255");
        doReturn(changed).when(profiles).getMergedProfile("Bambu Lab A1 0.4 nozzle","machine");
        var afterUpdate=new QuoteTimeNormalizationService(mapper,true,profiles);
        assertSame(raw,afterUpdate.normalize(raw,MACHINE,PLA,"PLA",NOZZLE,LAYER).quoteStats());
    }

    @Test
    void routesAll95ConfigurationsAndReproducesTheirIndependentValidation() throws Exception {
        JsonNode report;
        try(var input=getClass().getResourceAsStream("/quote-time/a1-p2s-v2-measurements.json")){report=mapper.readTree(input);}
        assertEquals(95,report.path("combinations").size());
        assertEquals(1520,report.path("measurements").size());
        int validation=0;var covered=new HashSet<String>();
        for(JsonNode row:report.path("measurements")) {
            JsonNode combination=null;
            for(JsonNode candidate:report.path("combinations"))if(candidate.path("id").equals(row.path("combination"))){combination=candidate;break;}
            assertNotNull(combination);
            var raw=mapper.treeToValue(row.path("p2s"),CalibrationPrintStats.class);
            for(JsonNode quality:combination.path("qualities")) {
                var result=service.normalize(raw,combination.path("p2sMachine").asText(),combination.path("p2sFilament").asText(),
                        combination.path("material").asText(),combination.path("nozzleMm").decimalValue(),combination.path("layerMm").decimalValue(),
                        combination.path("p2sProcess").asText(),quality.asText());
                assertEquals(row.path("combination").asText(),result.ruleId());covered.add(result.ruleId());
                double error=Math.abs((double)result.quoteStats().printTimeSeconds()/row.path("a1").path("printTimeSeconds").asLong()-1);
                assertTrue(error<0.124,row.path("key").asText()+": "+error);
                assertEquals(raw.filamentWeightGrams(),result.quoteStats().filamentWeightGrams());
                assertEquals(raw.filamentLengthMm(),result.quoteStats().filamentLengthMm());
            }
            if(row.path("group").asText().equals("validation"))validation++;
        }
        assertEquals(95,covered.size());assertEquals(380,validation);
    }

    @Test
    void linuxChecksAlsoSelectAllRulesAndStayWithinTheMeasuredError() throws Exception {
        JsonNode report;
        try(var input=getClass().getResourceAsStream("/quote-time/a1-p2s-v2-linux-checks.json")){report=mapper.readTree(input);}
        int pairs=0;
        for(var combination:report.path("combinations")) {
            for(var row:report.path("measurements")) {
                if(!row.path("combination").equals(combination.path("id")))continue;
                var raw=mapper.treeToValue(row.path("p2s"),CalibrationPrintStats.class);
                var result=service.normalize(raw,combination.path("p2sMachine").asText(),combination.path("p2sFilament").asText(),
                        combination.path("material").asText(),combination.path("nozzleMm").decimalValue(),combination.path("layerMm").decimalValue(),
                        combination.path("p2sProcess").asText(),combination.path("qualities").get(0).asText());
                assertEquals(combination.path("id").asText(),result.ruleId());
                assertTrue(Math.abs((double)result.quoteStats().printTimeSeconds()/row.path("a1").path("printTimeSeconds").asLong()-1)<0.065);
                pairs++;
            }
        }
        assertEquals(190,pairs);
    }
}

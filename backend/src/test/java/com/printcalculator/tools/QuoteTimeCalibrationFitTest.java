package com.printcalculator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class QuoteTimeCalibrationFitTest {
    @TempDir Path directory;
    private final ObjectMapper mapper=new ObjectMapper();

    @Test
    void finalValidationMeasurementsCannotChangeCurvesOrStartup() throws Exception {
        ObjectNode report;
        try(var input=getClass().getResourceAsStream("/quote-time/a1-p2s-v3-measurements.json")){report=(ObjectNode)mapper.readTree(input);}
        Path source=directory.resolve("measurements.json"),baseline=directory.resolve("baseline.json"),changed=directory.resolve("changed.json");
        mapper.writeValue(source.toFile(),report);
        QuoteTimeCalibrationFit.main(new String[]{source.toString(),baseline.toString()});
        for(var row:report.path("measurements")) {
            if(row.path("group").asText().equals("validation")) {
                var a1=(ObjectNode)row.path("a1");a1.put("printTimeSeconds",a1.path("printTimeSeconds").asLong()+100000);break;
            }
        }
        mapper.writeValue(source.toFile(),report);
        QuoteTimeCalibrationFit.main(new String[]{source.toString(),changed.toString()});
        var before=mapper.readTree(baseline.toFile());var after=mapper.readTree(changed.toFile());
        assertEquals(79,before.path("rules").size());
        for(int i=0;i<79;i++)assertEquals(before.path("rules").get(i).path("curve"),after.path("rules").get(i).path("curve"));
        assertTrue(after.path("validationMaxErrorPercent").asDouble()>before.path("validationMaxErrorPercent").asDouble());
    }

    @Test
    void inventoryPlanCoversEverySharedQualityAndPreservesTechnicalExclusions() throws Exception {
        Path inventory=directory.resolve("inventory.json"),work=directory.resolve("work");
        try(var input=getClass().getResourceAsStream("/quote-time/a1-p2s-v2-inventory.json")){Files.copy(input,inventory);}
        QuoteTimeCalibrationMatrix.main(new String[]{inventory.toString(),work.toString(),"--plan"});
        var plan=mapper.readTree(work.resolve("plan.json").toFile());
        assertEquals(79,plan.path("combinations").size());assertEquals(22,plan.path("fixtures").size());
        int qualities=0;
        for(var combination:plan.path("combinations")) {
            qualities+=combination.path("qualities").size();
            assertNotEquals("PC", combination.path("material").asText());
            assertFalse(combination.path("material").asText().equals("TPU")
                    && combination.path("nozzleMm").asDouble()==0.2);
        }
        assertEquals(159,qualities);
    }
}

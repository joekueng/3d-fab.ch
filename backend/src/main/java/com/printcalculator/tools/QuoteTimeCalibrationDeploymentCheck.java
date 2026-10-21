package com.printcalculator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.service.ProfileManager;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Offline replay against supplied profiles/resources; does not verify or modify website pricing. */
public final class QuoteTimeCalibrationDeploymentCheck {
    private QuoteTimeCalibrationDeploymentCheck() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: measurement-report.json");
        Logger.getLogger(ProfileManager.class.getName()).setLevel(Level.WARNING);
        var mapper=new ObjectMapper();
        var profiles=new ProfileManager(System.getenv().getOrDefault("PROFILES_DIR","profiles"),mapper);
        var service=new QuoteTimeNormalizationService(mapper,true,profiles);
        var report=mapper.readTree(Path.of(args[0]).toFile());int checked=0;
        for(var combination:report.path("combinations")) {
            var first=java.util.stream.StreamSupport.stream(report.path("measurements").spliterator(),false)
                    .filter(row->row.path("combination").equals(combination.path("id"))&&row.has("p2s")).findFirst().orElseThrow();
            var raw=mapper.treeToValue(first.path("p2s"),CalibrationPrintStats.class);
            var normalized=service.normalize(raw,combination.path("p2sMachine").asText(),combination.path("p2sFilament").asText(),
                    combination.path("material").asText(),combination.path("nozzleMm").decimalValue(),combination.path("layerMm").decimalValue(),
                    combination.path("p2sProcess").asText(),combination.path("qualities").get(0).asText());
            if(!normalized.ruleId().equals(combination.path("id").asText()))throw new IllegalStateException("Unmatched calibration: "+combination.path("id"));
            checked++;
        }
        System.out.println("VERIFIED offline profile fingerprints and experimental replay: "+checked+" configurations");
    }
}

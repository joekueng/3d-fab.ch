package com.printcalculator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.tools.QuoteTimeCurve;
import com.printcalculator.tools.A1TimeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fits only the calibration group; final validation rows never influence the curve. */
public final class QuoteTimeCalibrationFit {
    public static final long MIN_MODEL_SECONDS = 1800;
    private static final List<String> TRAINING_MODELS = List.of("cube30", "cube50", "plate100", "plate80", "block60", "tube30", "cube80", "block120", "tube80");
    private static final List<String> VALIDATION_MODELS = List.of("cube65", "cylinder60", "plate150");
    private QuoteTimeCalibrationFit() {}

    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: complete-results.json output-calibration.json");
        var mapper=new ObjectMapper();var report=mapper.readTree(Path.of(args[0]).toFile());
        List<Map<String,Object>> rules=new ArrayList<>();
        List<Double> validationErrors=new ArrayList<>();
        for(JsonNode combination:report.path("combinations")) {
            if (!A1TimeReference.supports(combination.path("material").asText(), combination.path("nozzleMm").decimalValue())) continue;
            List<JsonNode> training=new ArrayList<>(),validation=new ArrayList<>();
            for(JsonNode row:report.path("measurements")) {
                if(!row.path("combination").asText().equals(combination.path("id").asText()))continue;
                if(row.has("failure")||!row.has("a1")||!row.has("p2s"))throw new IllegalArgumentException("Incomplete comparison: "+row.path("key"));
                if (row.path("group").asText().equals("calibration") && TRAINING_MODELS.contains(row.path("model").asText()) && representative(row)) training.add(row);
                if (row.path("group").asText().equals("validation") && VALIDATION_MODELS.contains(row.path("model").asText())) validation.add(row);
            }
            if(training.size()<2||validation.size()!=3)throw new IllegalArgumentException("Expected at least 2 representative training pairs and 3 new validation pairs for "+combination.path("id"));
            QuoteTimeCurve curve=fit(training);
            Map<String,Object> rule=mapper.convertValue(combination,new com.fasterxml.jackson.core.type.TypeReference<>(){});
            rule.put("curve",curve);
            rule.put("calibrationSamples",training.size());rule.put("validationSamples",validation.size());
            rule.put("minModelSeconds", MIN_MODEL_SECONDS);
            rule.put("minSecondsPerLayer", training.stream().mapToDouble(QuoteTimeCalibrationFit::secondsPerLayer).min().orElseThrow());
            rule.put("maxSecondsPerLayer", training.stream().mapToDouble(QuoteTimeCalibrationFit::secondsPerLayer).max().orElseThrow());
            rule.put("preparationSource", "A1 total-minus-model slicer estimate; not physically timed");
            var trainingErrors=errors(curve,training);var errors=errors(curve,validation);validationErrors.addAll(errors);
            rule.put("calibrationMaxErrorPercent",trainingErrors.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
            rule.put("validationMaxErrorPercent",errors.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
            rule.put("validationMedianErrorPercent",median(errors));
            rule.put("enabled", errors.stream().mapToDouble(Double::doubleValue).max().orElseThrow() <= 10);
            rules.add(rule);
        }
        var calibration=new LinkedHashMap<String,Object>();calibration.put("schemaVersion",3);
        calibration.put("version",System.getenv().getOrDefault("CALIBRATION_VERSION","p2s-a1-2026-10-20-v3"));calibration.put("reference","Bambu Lab A1");
        calibration.put("sourceRuntime",report.path("runtime").asText());calibration.put("inventorySha256",report.path("inventorySha256").asText());
        calibration.put("validationMedianErrorPercent",median(validationErrors));
        calibration.put("validationMaxErrorPercent",validationErrors.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        calibration.put("rules",rules);
        Path output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),calibration);
        System.out.printf(java.util.Locale.ROOT,"FIT rules=%d validationPairs=%d medianError=%.3f%% maxError=%.3f%%%n",
                rules.size(),validationErrors.size(),median(validationErrors),calibration.get("validationMaxErrorPercent"));
    }

    private static boolean representative(JsonNode row) {
        return row.path("a1").path("modelPrintTimeSeconds").asLong() >= MIN_MODEL_SECONDS
                && row.path("p2s").path("modelPrintTimeSeconds").asLong() >= MIN_MODEL_SECONDS;
    }
    private static double secondsPerLayer(JsonNode row) {
        return row.path("p2s").path("modelPrintTimeSeconds").asDouble() / row.path("p2s").path("layerCount").asInt();
    }

    static QuoteTimeCurve fit(List<JsonNode> rows) {
        List<Double> startup=new ArrayList<>();List<Block> points=new ArrayList<>();
        for(var row:rows) {
            var a1=row.path("a1");var p2s=row.path("p2s");
            double layers=p2s.path("layerCount").asInt();
            if(layers<=0||p2s.path("modelPrintTimeSeconds").asLong()<=0)throw new IllegalArgumentException("Missing model metadata");
            startup.add((double)(a1.path("printTimeSeconds").asLong()-a1.path("modelPrintTimeSeconds").asLong()));
            points.add(new Block(p2s.path("modelPrintTimeSeconds").asDouble()/layers,
                    a1.path("modelPrintTimeSeconds").asDouble()/layers,1));
        }
        points.sort(Comparator.comparingDouble(Block::x));
        List<Block> blocks=new ArrayList<>();
        for(Block point:points) {
            blocks.add(point);
            while(blocks.size()>1) {
                int last=blocks.size()-1;Block a=blocks.get(last-1),b=blocks.get(last);
                if(a.y()<=b.y()&&Math.abs(a.x()-b.x())>=1e-8)break;
                int weight=a.weight()+b.weight();
                blocks.remove(last);blocks.set(last-1,new Block((a.x()*a.weight()+b.x()*b.weight())/weight,
                        (a.y()*a.weight()+b.y()*b.weight())/weight,weight));
            }
        }
        return new QuoteTimeCurve(Math.round(median(startup)),blocks.stream().map(b->new QuoteTimeCurve.Knot(b.x(),b.y())).toList());
    }
    static List<Double> errors(QuoteTimeCurve curve,List<JsonNode> rows) {
        return rows.stream().map(row->{
            var p=row.path("p2s");long seconds=curve.modelSeconds(p.path("modelPrintTimeSeconds").asLong(),p.path("layerCount").asInt())+curve.startupSeconds();
            return Math.abs((double)seconds/row.path("a1").path("printTimeSeconds").asLong()-1)*100;
        }).toList();
    }
    private static double median(List<Double> values) {
        var sorted=values.stream().sorted().toList();int middle=sorted.size()/2;
        return sorted.size()%2==0?(sorted.get(middle-1)+sorted.get(middle))/2:sorted.get(middle);
    }
    private record Block(double x,double y,int weight) {}
}

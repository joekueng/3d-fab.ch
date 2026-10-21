package com.printcalculator.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.tools.QuoteTimeCurve;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone interactive matrix; no frontend server, external scripts or customer files. */
public final class QuoteTimeCalibrationReport {
    private QuoteTimeCalibrationReport() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("Usage: results.json calibration.json linux-results.json output.html");
        var mapper=new ObjectMapper();
        var measurements=mapper.readTree(Path.of(args[0]).toFile());
        var calibration=mapper.readTree(Path.of(args[1]).toFile());
        var linux=mapper.readTree(Path.of(args[2]).toFile());
        Map<String,JsonNode> linuxRows=new LinkedHashMap<>();
        for(var row:linux.path("measurements"))if(row.has("a1")&&row.has("p2s"))linuxRows.put(row.path("key").asText(),row);
        var rows=new ArrayList<Map<String,Object>>();
        for(var rule:calibration.path("rules")) {
            String id=rule.path("id").asText();
            var curve=mapper.treeToValue(rule.path("curve"),QuoteTimeCurve.class);
            var row=new LinkedHashMap<String,Object>();
            row.put("id",id);row.put("material",rule.path("material").asText());row.put("nozzle",rule.path("nozzleMm").asDouble());
            row.put("layer",rule.path("layerMm").asDouble());row.put("qualities",rule.path("qualities"));
            for(String key:new String[]{"a1Process","p2sProcess","a1Filament","p2sFilament","validationMaxErrorPercent","enabled","calibrationSamples"})row.put(key,rule.path(key));
            var samples=new ArrayList<Map<String,Object>>();
            for(var measurement:measurements.path("measurements")) {
                if(!measurement.path("combination").asText().equals(id))continue;
                var sample=sample(curve,measurement);sample.put("model",measurement.path("model").asText());sample.put("group",measurement.path("group").asText());
                var linuxRow=linuxRows.get(measurement.path("key").asText());
                if(linuxRow!=null){
                    var checked=sample(curve,linuxRow);
                    double difference=Math.max(Math.abs(linuxRow.path("a1").path("printTimeSeconds").asDouble()/measurement.path("a1").path("printTimeSeconds").asDouble()-1),
                            Math.abs(linuxRow.path("p2s").path("printTimeSeconds").asDouble()/measurement.path("p2s").path("printTimeSeconds").asDouble()-1))*100;
                    checked.put("platformDifferencePercent",difference);sample.put("linux",checked);
                }
                samples.add(sample);
            }
            row.put("samples",samples);rows.add(row);
        }
        Map<String,String> labels=Map.ofEntries(Map.entry("cube10","Cubo 10 mm"),Map.entry("cube20","Cubo 20 mm"),Map.entry("cube50","Cubo 50 mm"),
                Map.entry("rod6","Asta 6 × 6 × 40 mm"),Map.entry("tower12","Torre 12 × 12 × 80 mm"),Map.entry("plate100","Piastra 100 × 100 × 2 mm"),
                Map.entry("block60","Blocco 60 × 40 × 8 mm"),Map.entry("cylinder16","Cilindro Ø16 × 40 mm"),Map.entry("cube30","Cubo 30 mm"),
                Map.entry("rod4","Asta 4 × 4 × 50 mm"),Map.entry("plate80","Piastra 80 × 40 × 3 mm"),Map.entry("tube30","Tubo quadro 30 × 30 × 30 mm"),
                Map.entry("cube25","Cubo 25 mm"),Map.entry("rod5","Asta 5 × 5 × 35 mm"),Map.entry("plate60","Piastra 60 × 50 × 2,5 mm"),
                Map.entry("tube24","Tubo quadro 24 × 24 × 35 mm"),
                Map.entry("cube80","Cubo 80 mm"),Map.entry("block120","Blocco 120 × 80 × 20 mm"),
                Map.entry("tube80","Tubo quadro 80 × 80 × 80 mm"),Map.entry("cube65","Cubo 65 mm"),
                Map.entry("cylinder60","Cilindro Ø60 × 70 mm"),Map.entry("plate150","Piastra 150 × 100 × 5 mm"));
        var models=new ArrayList<Map<String,Object>>();
        for(var fixture:measurements.path("fixtures"))models.add(Map.of("id",fixture.path("id").asText(),"label",labels.get(fixture.path("id").asText()),"group",fixture.path("group").asText()));
        var data=new LinkedHashMap<String,Object>();data.put("version",calibration.path("version"));data.put("sourceRuntime",calibration.path("sourceRuntime"));
        data.put("createdAt",measurements.path("createdAt"));data.put("comparisons",measurements.path("measurements").size());
        data.put("linuxComparisons",linuxRows.size());data.put("validationMedianErrorPercent",calibration.path("validationMedianErrorPercent"));
        data.put("validationMaxErrorPercent",calibration.path("validationMaxErrorPercent"));data.put("rows",rows);data.put("models",models);
        String html=resource("template.html").replace("@@CSS@@",resource("style.css")).replace("@@SCRIPT@@",resource("report.js"))
                .replace("@@DATA@@",mapper.writeValueAsString(data).replace("<","\\u003c"));
        Path output=Path.of(args[3]);Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,html,StandardCharsets.UTF_8);
        System.out.println("MATRIX "+output.toAbsolutePath()+" configurations="+rows.size()+" Linux checks="+linuxRows.size());
    }
    private static Map<String,Object> sample(QuoteTimeCurve curve,JsonNode row) {
        long a1=row.path("a1").path("printTimeSeconds").asLong(),p2s=row.path("p2s").path("printTimeSeconds").asLong();
        long normalized=curve.modelSeconds(row.path("p2s").path("modelPrintTimeSeconds").asLong(),row.path("p2s").path("layerCount").asInt())+curve.startupSeconds();
        var result=new LinkedHashMap<String,Object>();result.put("a1Seconds",a1);result.put("p2sSeconds",p2s);result.put("normalizedSeconds",normalized);
        long a1Model=row.path("a1").path("modelPrintTimeSeconds").asLong(),p2sModel=row.path("p2s").path("modelPrintTimeSeconds").asLong();
        result.put("a1ModelSeconds",a1Model);result.put("p2sModelSeconds",p2sModel);
        result.put("a1PreparationSeconds",a1-a1Model);result.put("p2sPreparationSeconds",p2s-p2sModel);
        result.put("nativeDifferencePercent",100*((double)p2s/a1-1));result.put("ratio",(double)a1/p2s);result.put("errorPercent",100*((double)normalized/a1-1));
        return result;
    }
    private static String resource(String name) throws Exception {
        try(var input=QuoteTimeCalibrationReport.class.getResourceAsStream("/quote-time/report/"+name)) {
            if(input==null)throw new IllegalArgumentException("Missing report resource: "+name);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}

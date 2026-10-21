package com.printcalculator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.StepConversionService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline, sequential calibration. No database access and no automatic factor changes. */
public final class QuoteTimeCalibration {
    private QuoteTimeCalibration() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Usage: report.json model1.stl model2.stl [more models.stl]");
        var mapper = new ObjectMapper();
        var profiles = new ProfileManager(System.getenv().getOrDefault("PROFILES_DIR", "profiles"), mapper);
        String slicerPath = System.getenv().getOrDefault("SLICER_PATH",
                System.getProperty("os.name").startsWith("Mac")
                        ? "/Applications/OrcaSlicer.app/Contents/MacOS/OrcaSlicer" : "/opt/orcaslicer/AppRun");
        var slicer = new CalibrationSlicer(slicerPath, "assimp", profiles, mapper,
                new StepConversionService(System.getenv().getOrDefault("STEP_PYTHON_PATH", "venv/bin/python"), 120));
        var comparisons = new ArrayList<Map<String, Object>>();
        var ratios = new ArrayList<Double>();
        var normalization = new QuoteTimeNormalizationService(mapper, true);
        for (int modelIndex = 1; modelIndex < args.length; modelIndex++) {
            Path model = Path.of(args[modelIndex]).toAbsolutePath();
            if (!Files.isRegularFile(model) || !model.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".stl")) {
                throw new IllegalArgumentException("Calibration requires existing STL files in millimetres");
            }
            String hash;
            try (var input = Files.newInputStream(model)) {
                var digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
                hash = HexFormat.of().formatHex(digest.digest());
            }
            for (String material : List.of("PLA", "PETG")) {
                CalibrationPrintStats a1 = slice(slicer, profiles, model, "A1", material);
                CalibrationPrintStats p2s = slice(slicer, profiles, model, "P2S", material);
                if (a1.printTimeSeconds() <= 0 || p2s.printTimeSeconds() <= 0) {
                    throw new IllegalStateException("A calibration run produced no print time");
                }
                double ratio = (double) a1.printTimeSeconds() / p2s.printTimeSeconds();
                ratios.add(ratio);
                var pair = new LinkedHashMap<String, Object>();
                pair.put("model", model.getFileName().toString());
                pair.put("sha256", hash);
                pair.put("material", material);
                pair.put("a1Seconds", a1.printTimeSeconds());
                pair.put("p2sSeconds", p2s.printTimeSeconds());
                pair.put("a1ModelSeconds", a1.modelPrintTimeSeconds());
                pair.put("p2sModelSeconds", p2s.modelPrintTimeSeconds());
                pair.put("a1LayerCount", a1.layerCount());
                pair.put("p2sLayerCount", p2s.layerCount());
                pair.put("a1Grams", a1.filamentWeightGrams());
                pair.put("p2sGrams", p2s.filamentWeightGrams());
                pair.put("a1OverP2s", ratio);
                var corrected = normalization.normalize(p2s, "Bambu Lab P2S 0.4 nozzle",
                        filamentProfile("P2S", material), material, new BigDecimal("0.4"), new BigDecimal("0.2"));
                pair.put("normalizedSeconds", corrected.quoteStats().printTimeSeconds());
                pair.put("calibrationVersion", corrected.calibrationVersion());
                pair.put("errorPercent", 100 * ((double) corrected.quoteStats().printTimeSeconds() / a1.printTimeSeconds() - 1));
                comparisons.add(pair);
                System.out.printf(java.util.Locale.ROOT, "%s %s: A1=%ds, P2S=%ds, factor=%.4f%n",
                        model.getFileName(), material, a1.printTimeSeconds(), p2s.printTimeSeconds(), ratio);
            }
        }
        double factor = median(ratios);
        List<Double> errors = comparisons.stream().map(pair -> Math.abs(
                ((Number) pair.get("p2sSeconds")).doubleValue() * factor
                        / ((Number) pair.get("a1Seconds")).doubleValue() - 1) * 100).toList();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("createdAt", Instant.now().toString());
        report.put("source", "OrcaSlicer estimates, not measured physical prints");
        report.put("settings", Map.of("nozzleMm", 0.4, "layerMm", 0.2, "infillPercent", 15,
                "infillPattern", "grid", "supports", true));
        report.put("comparisons", comparisons);
        report.put("medianFactor", factor);
        report.put("medianAbsoluteErrorPercent", median(errors));
        report.put("maxAbsoluteErrorPercent", errors.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        var normalizedErrors = comparisons.stream().map(pair -> Math.abs(((Number) pair.get("errorPercent")).doubleValue())).toList();
        report.put("normalizedMedianAbsoluteErrorPercent", median(normalizedErrors));
        report.put("normalizedMaxAbsoluteErrorPercent", normalizedErrors.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Report: " + output + "; median factor=" + factor);
    }

    private static CalibrationPrintStats slice(CalibrationSlicer slicer, ProfileManager profiles, Path model,
                                    String machine, String material) throws Exception {
        String printer = "Bambu Lab " + machine + " 0.4 nozzle";
        String process = profiles.findCompatibleProcessProfileName(printer, new BigDecimal("0.200"), "standard").orElseThrow();
        return slicer.slice(model.toFile(), printer, filamentProfile(machine, material), process, null,
                Map.of("layer_height", "0.2", "sparse_infill_density", "15%",
                        "sparse_infill_pattern", "grid", "enable_support", "1"));
    }

    private static String filamentProfile(String machine, String material) {
        return machine.equals("A1")
                ? (material.equals("PLA") ? "Bambu PLA Basic @BBL A1" : "Bambu PETG Basic @BBL A1")
                : (material.equals("PLA") ? "Bambu PLA Basic @BBL P2S" : "Generic PETG @BBL P2S");
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }
}

package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Opt-in comparison with the production Orca version and a local model requiring supports.
 */
@EnabledIfEnvironmentVariable(named = "PA12_ASA_MODEL_PATH", matches = ".+")
class Pa12AsaNativeSlicingTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"PA-CF", "PET-CF"})
    void asaSupportsProduceTwoFilamentsAndIncreaseTheNativeEstimate(String material) throws Exception {
        String executable = System.getenv("SLICER_PATH");
        assertNotNull(executable, "Set SLICER_PATH to OrcaSlicer 2.4.2");
        Path captureDir = System.getenv("PA12_ASA_CAPTURE_DIR") == null ? tempDir
                : Path.of(System.getenv("PA12_ASA_CAPTURE_DIR"));
        captureDir = captureDir.resolve(material);
        Files.createDirectories(captureDir);
        Path captured = captureDir.resolve("last.gcode");
        Path wrapper = Files.writeString(tempDir.resolve("orca-probe"), """
                #!/bin/sh
                for config in *.json; do
                  if [ -f "$config" ]; then cp "$config" '%s/'; fi
                done
                '%s' "$@"
                result=$?
                if [ -f result.json ]; then cp result.json '%s/'; fi
                for output in *.gcode; do
                  if [ -f "$output" ]; then cp "$output" '%s'; fi
                done
                exit "$result"
                """.formatted(captureDir.toString().replace("'", "'\\''"), executable.replace("'", "'\\''"),
                captureDir.toString().replace("'", "'\\''"), captured.toString().replace("'", "'\\''")));
        assertTrue(wrapper.toFile().setExecutable(true));
        var mapper = new ObjectMapper();
        var profiles = new ProfileManager("profiles", mapper);
        var slicer = new SlicerService(wrapper.toString(), "assimp", profiles, new GCodeParser(), mapper,
                mock(StepConversionService.class));
        var model = Path.of(System.getenv("PA12_ASA_MODEL_PATH")).toFile();
        String machine = "Bambu Lab P2S 0.4 nozzle";
        String process = profiles.findCompatibleProcessProfileName(machine, new BigDecimal("0.24"), "draft")
                .orElseThrow();
        var settings = Map.of("enable_support", "1", "layer_height", "0.24",
                "sparse_infill_density", "15%", "sparse_infill_pattern", "grid");
        String p2sFilament = material.equals("PA-CF") ? "Generic PA-CF" : "Bambu PET-CF @BBL X1C";
        String x2dFilament = (material.equals("PA-CF") ? "Generic PA-CF" : "Bambu PET-CF") + " @BBL X2D 0.4 nozzle";
        var single = slicer.slice(model, machine, p2sFilament, process, null, settings);
        var multi = slicer.slice(model, machine, p2sFilament, process, null, settings, "Generic ASA", "#000000");
        System.out.println(material + " P2S single material: " + single);
        System.out.println(material + " P2S with ASA supports: " + multi);
        String p2sGcode = Files.readString(captured);
        assertTrue(p2sGcode.contains("filament_map = 1,1"));
        String x2d = "Bambu Lab X2D 0.4 nozzle";
        String x2dProcess = profiles.findCompatibleProcessProfileName(x2d, new BigDecimal("0.24"), "draft")
                .orElseThrow();
        var x2dSingle = slicer.slice(model, x2d, x2dFilament, x2dProcess, null, settings);
        var x2dMulti = slicer.slice(model, x2d, x2dFilament, x2dProcess, null, settings,
                "Generic ASA @BBL X2D 0.4 nozzle", "#000000");
        System.out.println(material + " X2D single material: " + x2dSingle);
        System.out.println(material + " X2D with ASA supports: " + x2dMulti);
        assertTrue(x2dMulti.printTimeSeconds() > x2dSingle.printTimeSeconds());
        String gcode = Files.readString(captured);
        assertTrue(gcode.contains("filament_map = 1,2"));
        assertAuxiliarySupportAcceleration(gcode);
        System.out.println("X2D tool commands: " + gcode.lines().filter(line -> line.matches("T[01] H.*"))
                .distinct().toList());
        assertTrue(java.util.regex.Pattern.compile("(?m)^T0(?:\\s|$)").matcher(gcode).find(),
                "The job must load the model material");
        assertTrue(java.util.regex.Pattern.compile("(?m)^T1(?:\\s|$)").matcher(gcode).find(),
                "The job must switch to ASA");
        var usage = java.util.regex.Pattern.compile("(?m)^; filament used \\[g\\] = ([^\\r\\n]+)")
                .matcher(gcode);
        assertTrue(usage.find());
        String[] weights = usage.group(1).split(",");
        assertEquals(2, weights.length);
        assertTrue(Double.parseDouble(weights[0].trim()) > 0);
        assertTrue(Double.parseDouble(weights[1].trim()) > 0, "ASA must actually be extruded");
        assertTrue(multi.printTimeSeconds() > single.printTimeSeconds(), "Use actual Orca tool-change time");
    }

    private void assertAuxiliarySupportAcceleration(String gcode) {
        int tool = -1;
        String feature = "";
        double acceleration = Double.NaN;
        int supportMoves = 0;
        var extrusion = java.util.regex.Pattern.compile("\\bE(-?[\\d.]+)");
        for (String line : gcode.lines().toList()) {
            if (line.matches("T[01](?:\\s.*)?")) tool = line.charAt(1) - '0';
            if (line.startsWith("; FEATURE: ")) feature = line.substring(11);
            if (line.startsWith("M204 S")) acceleration = Double.parseDouble(line.substring(6).split("[ ;]", 2)[0]);
            var amount = extrusion.matcher(line);
            if (tool == 1 && (feature.equals("Support") || feature.equals("Support interface")) && line.matches("G[123] .*")) {
                if (amount.find() && Double.parseDouble(amount.group(1)) > 0 &&
                        (line.contains(" X") || line.contains(" Y"))) {
                    assertTrue(acceleration <= 1000, "ASA support move must respect Bowden acceleration: " + line);
                    supportMoves++;
                }
            }
        }
        assertTrue(supportMoves > 0, "Verify real support extrusion, not only a profile header");
    }
}

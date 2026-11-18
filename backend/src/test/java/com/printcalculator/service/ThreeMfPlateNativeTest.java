package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Opt-in native check; customer geometry stays outside the repository.
 */
@EnabledIfEnvironmentVariable(named = "MULTIPLATE_3MF_PATH", matches = ".+")
class ThreeMfPlateNativeTest {
    @TempDir
    Path tempDir;

    @Test
    void realProjectMatchesTheSumOfIndependentlySlicedPlates() throws Exception {
        String executable = System.getenv("SLICER_PATH");
        assertNotNull(executable, "Set SLICER_PATH to the OrcaSlicer binary under test");
        Path input = Path.of(System.getenv("MULTIPLATE_3MF_PATH"));
        byte[] original = Files.readAllBytes(input);
        var mapper = new ObjectMapper();
        var profiles = new ProfileManager("profiles", mapper);
        var slicer = new SlicerService(executable, "assimp", profiles, new GCodeParser(), mapper,
                mock(StepConversionService.class));
        String machine = "Bambu Lab P2S 0.4 nozzle";
        String process = profiles.findCompatibleProcessProfileName(machine, new BigDecimal("0.20"), "standard").orElseThrow();
        var settings = Map.of("enable_support", "1", "layer_height", "0.20",
                "sparse_infill_density", "15%", "sparse_infill_pattern", "grid");
        var combined = slicer.slice(input.toFile(), machine, "Generic ASA", process, null, settings);
        assertTrue(combined.plates().size() > 1);
        var plates = new ModelConversionSupport("assimp").convert3mfPlates(input.toFile(), tempDir);
        assertEquals(plates.size(), combined.plates().size());
        long seconds = 0;
        double grams = 0;
        for (var plate : plates) {
            var stats = slicer.slice(plate.path().toFile(), machine, "Generic ASA", process, null, settings);
            var recorded = combined.plates().stream().filter(p -> p.plateNumber() == plate.number()).findFirst().orElseThrow();
            assertEquals(stats.printTimeSeconds(), recorded.printTimeSeconds());
            assertEquals(stats.filamentWeightGrams(), recorded.filamentWeightGrams(), 0.001);
            seconds += stats.printTimeSeconds();
            grams += stats.filamentWeightGrams();
            System.out.println("Plate " + plate.number() + ": " + stats);
        }
        assertEquals(seconds, combined.printTimeSeconds());
        assertEquals(grams, combined.filamentWeightGrams(), 0.001);
        assertArrayEquals(original, Files.readAllBytes(input));
        System.out.println("Combined project: " + combined);
    }
}

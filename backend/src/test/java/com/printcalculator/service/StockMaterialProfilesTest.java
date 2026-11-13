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

class StockMaterialProfilesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProfileManager profiles = new ProfileManager("profiles", mapper);
    @TempDir
    Path tempDir;

    @Test
    void initialPcAbsRecipeUsesPublishedDensityTemperatureAndCooling() throws Exception {
        var profile = profiles.getMergedProfile("Polymaker PC-ABS @BBL P2S", "filament");
        assertEquals(1.1, profile.path("filament_density").path(0).asDouble());
        assertEquals(260, profile.path("nozzle_temperature").path(0).asInt());
        assertEquals(100, profile.path("textured_plate_temp").path(0).asInt());
        assertEquals(0, profile.path("fan_max_speed").path(0).asInt());
        assertEquals(3.2, profile.path("filament_max_volumetric_speed").path(0).asDouble());
    }

    @Test
    void officialTpu85aRecipePreservesItsPointSixCompatibilityAndFlowLimit() throws Exception {
        var profile = profiles.getMergedProfile("Bambu TPU 85A @BBL P2S", "filament");
        assertEquals("Bambu Lab P2S 0.6 nozzle", profile.path("compatible_printers").path(0).asText());
        assertEquals(1, profile.path("compatible_printers").size());
        assertEquals(2.2, profile.path("filament_max_volumetric_speed").path(0).asDouble());
        assertEquals(225, profile.path("nozzle_temperature").path(0).asInt());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STOCK_MATERIAL_NATIVE", matches = "true")
    void initialRecipesProducePositiveNativeStatistics() throws Exception {
        String executable = System.getenv("SLICER_PATH");
        assertNotNull(executable);
        var slicer = new SlicerService(executable, "assimp", profiles, new GCodeParser(), mapper,
                mock(StepConversionService.class));
        Path cube = tempDir.resolve("cube.stl");
        double[][] vertices = {{0, 0, 0}, {10, 0, 0}, {10, 10, 0}, {0, 10, 0}, {0, 0, 10}, {10, 0, 10}, {10, 10, 10}, {0, 10, 10}};
        int[][] faces = {{0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7}, {0, 1, 5}, {0, 5, 4},
                {1, 2, 6}, {1, 6, 5}, {2, 3, 7}, {2, 7, 6}, {3, 0, 4}, {3, 4, 7}};
        var stl = new StringBuilder("solid cube\n");
        for (int[] face : faces) {
            stl.append("facet normal 0 0 0\nouter loop\n");
            for (int index : face)
                stl.append("vertex ").append(vertices[index][0]).append(' ')
                        .append(vertices[index][1]).append(' ').append(vertices[index][2]).append('\n');
            stl.append("endloop\nendfacet\n");
        }
        Files.writeString(cube, stl.append("endsolid cube\n").toString());
        for (String[] recipe : new String[][]{{"Polymaker PC-ABS @BBL P2S", "0.4", "0.20"},
                {"Bambu TPU 85A @BBL P2S", "0.6", "0.24"}}) {
            String machine = "Bambu Lab P2S " + recipe[1] + " nozzle";
            String process = profiles.findCompatibleProcessProfileName(machine, new BigDecimal(recipe[2]), "standard").orElseThrow();
            var stats = slicer.slice(cube.toFile(), machine, recipe[0], process, null,
                    Map.of("enable_support", "0", "layer_height", recipe[2], "sparse_infill_density", "15%"));
            assertTrue(stats.printTimeSeconds() > 0);
            assertTrue(stats.filamentWeightGrams() > 0);
            System.out.println(recipe[0] + ": " + stats);
        }
    }
}

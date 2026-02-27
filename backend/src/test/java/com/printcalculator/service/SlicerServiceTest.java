package com.printcalculator.service;

import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SlicerServiceTest {

    @Test
    void parseModelDimensionsFromInfoOutput_validOutput_returnsDimensions() {
        String output = """
                [file.stl]
                size_x = 130.860428
                size_y = 225.000000
                size_z = 140.000000
                min_x = 0.000000
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isPresent());
        assertEquals(130.860428, dimensions.get().xMm(), 0.000001);
        assertEquals(225.0, dimensions.get().yMm(), 0.000001);
        assertEquals(140.0, dimensions.get().zMm(), 0.000001);
    }

    @Test
    void parseModelDimensionsFromInfoOutput_withNoise_returnsDimensions() {
        String output = """
                [2026-02-27 10:26:30.306251] [0x1] [trace] Initializing StaticPrintConfigs
                [model.3mf]
                size_x = 97.909241
                size_y = 97.909241
                size_z = 70.000008
                [2026-02-27 10:26:30.314575] [0x1] [error] calc_exclude_triangles
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isPresent());
        assertEquals(97.909241, dimensions.get().xMm(), 0.000001);
        assertEquals(97.909241, dimensions.get().yMm(), 0.000001);
        assertEquals(70.000008, dimensions.get().zMm(), 0.000001);
    }

    @Test
    void parseModelDimensionsFromInfoOutput_missingValues_returnsEmpty() {
        String output = """
                [model.step]
                size_x = 10.0
                size_y = 20.0
                """;

        Optional<ModelDimensions> dimensions = SlicerService.parseModelDimensionsFromInfoOutput(output);

        assertTrue(dimensions.isEmpty());
    }
}

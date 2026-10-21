package com.printcalculator.service;

import com.printcalculator.exception.ModelProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModelConversionSupportTest {
    @TempDir Path tempDir;
    private final ModelConversionSupport converter = new ModelConversionSupport("missing-assimp");

    @ParameterizedTest
    @CsvSource({"mm,1", "cm,10", "m,1000", "in,25.4"})
    void everySupportedObjUnitProducesTheExpectedPhysicalBounds(String unit, double scale) throws Exception {
        String geometry = "v -1 -2 -3\nv 1 -2 -3\nv -1 2 3\nf 1 2 3\n";
        Path input = Files.writeString(tempDir.resolve("units.obj"), geometry);
        Path output = tempDir.resolve("units.stl");
        converter.convertMeshToPersistentStl(input.toFile(), output,
                com.printcalculator.model.ModelUnit.parse(unit).millimetres());

        var vertices = Files.readString(output).lines().map(String::trim)
                .filter(line -> line.startsWith("vertex "))
                .map(line -> java.util.Arrays.stream(line.substring(7).split(" "))
                        .mapToDouble(Double::parseDouble).toArray()).toList();
        for (int axis = 0; axis < 3; axis++) {
            final int coordinate = axis;
            double min = vertices.stream().mapToDouble(v -> v[coordinate]).min().orElseThrow();
            double max = vertices.stream().mapToDouble(v -> v[coordinate]).max().orElseThrow();
            assertEquals(2 * (axis + 1) * scale, max - min, 0.001);
        }
        assertEquals(geometry, Files.readString(input));
    }

    @Test
    void objUnitsScaleTheGeneratedMeshWithoutChangingTheOriginal() throws Exception {
        String original = "v 0 0 0\nv 0.1 0 0\nv 0 0.2 0.3\nf 1 2 3\n";
        Path input = Files.writeString(tempDir.resolve("metres.obj"), original);
        Path output = tempDir.resolve("millimetres.stl");
        converter.convertMeshToPersistentStl(input.toFile(), output,
                com.printcalculator.model.ModelUnit.parse("m").millimetres());
        String stl = Files.readString(output);
        double largest = stl.lines().map(String::trim).filter(line -> line.startsWith("vertex "))
                .flatMap(line -> java.util.Arrays.stream(line.substring(7).split(" ")))
                .mapToDouble(Double::parseDouble).max().orElseThrow();
        assertEquals(300, largest, 0.001);
        assertEquals(original, Files.readString(input));
    }

    @Test
    void objTriangulatesMultipleObjectsAndPreservesOriginal() throws Exception {
        String original = """
                mtllib /nonexistent/materials.mtl
                o first
                v 0 0 0
                v 20 0 0
                v 20 10 0
                v 0 10 0
                f 1 2 3 4
                o second
                v 0 0 5
                v 20 0 5
                v 0 10 5
                f -3 -2 -1
                """;
        Path input = Files.writeString(tempDir.resolve("model.obj"), original);
        Path output = tempDir.resolve("preview.stl");

        converter.convertMeshToPersistentStl(input.toFile(), output);

        String stl = Files.readString(output);
        assertEquals(3, stl.lines().filter(line -> line.trim().startsWith("facet normal")).count());
        assertTrue(stl.contains("20.0"));
        assertTrue(stl.contains("10.0"));
        assertTrue(stl.contains("5.0"));
        assertEquals(original, Files.readString(input));
    }

    @Test
    void threeMfIncludesEveryBuildInstanceAndTransform() throws Exception {
        Path input = tempDir.resolve("assembly.3mf");
        String model = """
                <?xml version="1.0" encoding="UTF-8"?>
                <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
                  <resources><object id="1" type="model"><mesh>
                    <vertices><vertex x="0" y="0" z="0"/><vertex x="2" y="0" z="0"/><vertex x="0" y="3" z="1"/></vertices>
                    <triangles><triangle v1="0" v2="1" v3="2"/></triangles>
                  </mesh></object></resources>
                  <build><item objectid="1"/><item objectid="1" transform="1 0 0 0 1 0 0 0 1 10 0 0"/></build>
                </model>
                """;
        try (var zip = new ZipOutputStream(Files.newOutputStream(input))) {
            zip.putNextEntry(new ZipEntry("3D/3dmodel.model"));
            zip.write(model.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] original = Files.readAllBytes(input);
        Path output = tempDir.resolve("assembly.stl");

        converter.convert3mfToPersistentStl(input.toFile(), output);

        String stl = Files.readString(output);
        assertEquals(2, stl.lines().filter(line -> line.trim().startsWith("facet normal")).count());
        assertTrue(stl.contains("12.0"));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test
    void invalidMeshDoesNotLeaveAPartialPreview() throws Exception {
        Path input = Files.writeString(tempDir.resolve("invalid.obj"), "not a model");
        Path output = tempDir.resolve("invalid.stl");

        assertThrows(ModelProcessingException.class,
                () -> converter.convertMeshToPersistentStl(input.toFile(), output));
        assertFalse(Files.exists(output));
    }
}

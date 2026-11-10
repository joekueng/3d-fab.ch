package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlicerMultiMaterialTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void passesBothProfilesToOrcaAndPreservesItsTotalTime(boolean oversized, boolean project) throws Exception {
        Path capturedProcess = tempDir.resolve("process.json");
        Path capturedSupport = tempDir.resolve("asa.json");
        Path capturedArgs = tempDir.resolve("args");
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                printf '%%s\\n' "$@" > '%s'
                cp process.json '%s'
                cp support-filament.json '%s'
                cat > part.gcode <<'GCODE'
                ; total estimated time: 5h 46m
                ; model printing time: 5h 29m
                ; filament used [g] = 53.06, 32.38
                GCODE
                """.formatted(capturedArgs, capturedProcess, capturedSupport));
        assertTrue(executable.toFile().setExecutable(true));
        ProfileManager profiles = mock(ProfileManager.class);
        ObjectNode machine = mapper.createObjectNode();
        ObjectNode model = mapper.createObjectNode();
        ObjectNode asa = mapper.createObjectNode();
        asa.putArray("filament_type").add("ASA");
        asa.putArray("nozzle_temperature").add("270");
        asa.putArray("filament_max_volumetric_speed").add("12");
        asa.putArray("default_filament_colour").add("#123456");
        ObjectNode sourceProcess = mapper.createObjectNode();
        sourceProcess.put("support_on_build_plate_only", "1");
        when(profiles.getMergedProfile("machine", "machine")).thenReturn(machine);
        when(profiles.getMergedProfile("model", "filament")).thenReturn(model);
        when(profiles.getMergedProfile("asa", "filament")).thenReturn(asa);
        when(profiles.getMergedProfile("process", "process")).thenReturn(sourceProcess);
        var slicer = spy(new SlicerService(executable.toString(), "assimp", profiles, new GCodeParser(), mapper,
                mock(StepConversionService.class)));
        doReturn(Optional.of(new ModelDimensions(10, 10, 10))).when(slicer).inspectModelDimensions(any());
        Path input = Files.writeString(tempDir.resolve("part.stl"), "mesh");
        if (project) {
            input = tempDir.resolve("part.3mf");
            try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(input))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("3D/3dmodel.model"));
                zip.write("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
                          <resources><object id="1" type="model"><mesh>
                            <vertices><vertex x="0" y="0" z="0"/><vertex x="2" y="0" z="0"/><vertex x="0" y="3" z="1"/></vertices>
                            <triangles><triangle v1="0" v2="1" v3="2"/></triangles>
                          </mesh></object></resources><build><item objectid="1"/></build>
                        </model>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
                // Imported per-object settings must not override the PA12-CF/ASA policy.
                zip.putNextEntry(new java.util.zip.ZipEntry("Metadata/project_settings.config"));
                zip.write("{\"support_filament\":\"1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        var settings = Map.of("enable_support", "1");
        var stats = oversized
                ? slicer.sliceForOversizedEstimate(input.toFile(), "machine", "model", "process",
                Optional.of(new ModelDimensions(270, 30, 30)), settings, "asa", "#000000")
                : slicer.slice(input.toFile(), "machine", "model", "process", null, settings, "asa", "#000000");

        var args = Files.readAllLines(capturedArgs);
        if (project) {
            assertFalse(args.contains(input.toString()), "Slice normalized geometry instead of project overrides");
            assertTrue(args.getLast().endsWith(".stl"));
        }
        String[] paths = args.get(args.indexOf("--load-filaments") + 1).split(";");
        assertEquals(2, paths.length);
        assertTrue(paths[0].endsWith("/filament.json"));
        assertTrue(paths[1].endsWith("/support-filament.json"));
        var process = mapper.readTree(capturedProcess.toFile());
        assertEquals("2", process.path("support_filament").asText());
        assertEquals("2", process.path("support_interface_filament").asText());
        assertEquals("1", process.path("wall_filament").asText());
        assertEquals("1", process.path("enable_prime_tower").asText());
        assertEquals("0", process.path("flush_into_objects").asText());
        assertEquals("0", process.path("flush_into_infill").asText());
        assertEquals("0", process.path("support_on_build_plate_only").asText());
        assertEquals("#000000", process.path("filament_colour").path(0).asText());
        assertEquals("#123456", process.path("filament_colour").path(1).asText());
        var loadedAsa = mapper.readTree(capturedSupport.toFile());
        assertEquals("270", loadedAsa.path("nozzle_temperature").path(0).asText());
        assertEquals("12", loadedAsa.path("filament_max_volumetric_speed").path(0).asText());
        assertFalse(loadedAsa.has("filament_is_support"), "Do not reclassify ordinary ASA as special support filament");
        assertEquals(20760, stats.printTimeSeconds());
        assertEquals(85.44, stats.filamentWeightGrams(), 0.001);
    }

    @Test
    void rejectsAnIncorrectSupportProfileBeforeRunningTheCli() throws Exception {
        ProfileManager profiles = mock(ProfileManager.class);
        when(profiles.getMergedProfile(any(), any())).thenAnswer(invocation -> mapper.createObjectNode());
        var slicer = new SlicerService("/not-a-slicer", "assimp", profiles, new GCodeParser(), mapper,
                mock(StepConversionService.class));
        var failure = assertThrows(IOException.class, () -> slicer.slice(tempDir.resolve("part.stl").toFile(),
                "machine", "model", "process", null, Map.of("enable_support", "1"), "PLA", "#000000"));
        assertTrue(failure.getMessage().contains("must be ASA"));
    }
}

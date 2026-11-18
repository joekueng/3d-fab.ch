package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static com.printcalculator.service.ThreeMfPlateFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ThreeMfPlateSlicingTest {
    private final ModelConversionSupport converter = new ModelConversionSupport("unused-assimp");
    @TempDir
    Path tempDir;

    @Test
    void extractsExternalMeshesRepeatedInstancesAndTransformsWithoutChangingTheUpload() throws Exception {
        Path input = project(tempDir.resolve("project.3mf"), plate(1, 0) + plate(4, 1) + plate(9, -1));
        byte[] original = Files.readAllBytes(input);
        var plates = converter.convert3mfPlates(input.toFile(), tempDir.resolve("plates"));
        assertEquals(java.util.List.of(1, 4), plates.stream().map(ModelConversionSupport.PlateModel::number).toList());
        for (var plate : plates) {
            String stl = Files.readString(plate.path());
            assertEquals(4, stl.lines().filter(line -> line.startsWith("facet normal")).count());
        }
        assertTrue(Files.readString(plates.getFirst().path()).contains("vertex 20.0 0.0 0.0"));
        assertTrue(Files.readString(plates.getLast().path()).contains("vertex 420.0 -300.0 0.0"));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test
    void noPlateMetadataRetainsTheEstablishedFallback() throws Exception {
        Path input = project(tempDir.resolve("project.3mf"), null);
        assertTrue(converter.convert3mfPlates(input.toFile(), tempDir.resolve("plates")).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unassigned", "duplicate", "unknown", "duplicatePlate", "tooMany"})
    void cannotSilentlyOmitOrDoubleCountGeometry(String problem) throws Exception {
        String metadata = switch (problem) {
            case "unassigned" -> plate(1, 0);
            case "duplicate" -> plate(1, 0) + plate(2, 0) + plate(3, 1);
            case "unknown" -> plate(1, 0) + plate(2, 99);
            case "duplicatePlate" -> plate(1, 0) + plate(1, 1);
            default -> java.util.stream.IntStream.rangeClosed(1, 33).mapToObj(n -> plate(n, -1))
                    .collect(java.util.stream.Collectors.joining());
        };
        Path input = project(tempDir.resolve("project.3mf"), metadata);
        var failure = assertThrows(ModelProcessingException.class,
                () -> converter.convert3mfPlates(input.toFile(), tempDir.resolve("plates")));
        assertEquals("MODEL_PLATE_LAYOUT_INVALID", failure.getCode());
    }

    @Test
    void sumsEveryPlateAndKeepsSeparateStartTimesWithoutRequestingSplitConsent() throws Exception {
        var slicer = slicer(false);
        Path input = project(tempDir.resolve("project.3mf"), plate(1, 0) + plate(4, 1));
        var stats = slicer.slice(input.toFile(), "machine", "filament", "process", null, Map.of("enable_support", "1"));
        assertEquals(240, stats.printTimeSeconds());
        assertEquals(5, stats.filamentWeightGrams());
        assertEquals(50, stats.filamentLengthMm());
        assertEquals(java.util.List.of(1, 4), stats.plates().stream().map(p -> p.plateNumber()).toList());
        var calls = Files.readAllLines(tempDir.resolve("calls"));
        assertEquals(2, calls.size());
        assertTrue(calls.stream().noneMatch(call -> call.contains("project.3mf")));
        assertTrue(calls.stream().allMatch(call -> call.contains("plate-") && call.contains(".stl")));
        for (var call : calls) {
            Path stl = Path.of(call.substring(call.lastIndexOf(' ') + 1));
            assertFalse(Files.exists(stl.getParent()), "Temporary plate geometry is removed");
        }
    }

    @Test
    void failedPlateDoesNotReturnAPartialQuote() throws Exception {
        var slicer = slicer(true);
        Path input = project(tempDir.resolve("project.3mf"), plate(1, 0) + plate(4, 1));
        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));
        assertEquals("SLICER_EXECUTION_FAILED", failure.getCode());
    }

    private SlicerService slicer(boolean failSecond) throws Exception {
        return slicer(failSecond, false);
    }

    @Test
    void splitConsentEnlargesOnlyThePlateThatExceedsThePhysicalVolume() throws Exception {
        var slicer = slicer(false, true);
        doAnswer(call -> {
            java.io.File input = call.getArgument(0);
            return Optional.of(input.getName().equals("plate-4.stl")
                    ? new ModelDimensions(300, 30, 30) : new ModelDimensions(20, 10, 5));
        }).when(slicer).inspectModelDimensions(any());
        Path input = project(tempDir.resolve("project.3mf"), plate(1, 0) + plate(4, 1));
        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));
        assertEquals("MODEL_OUT_OF_PRINT_VOLUME", failure.getCode());
        Files.delete(tempDir.resolve("calls"));
        var stats = slicer.sliceForOversizedEstimate(input.toFile(), "machine", "filament", "process",
                Optional.of(new ModelDimensions(500, 300, 30)), null);
        assertEquals(2, stats.plates().size());
        assertEquals(240, stats.printTimeSeconds());
        var calls = Files.readAllLines(tempDir.resolve("calls"));
        assertEquals(1, calls.stream().filter(call -> call.endsWith("plate-1.stl")).count());
        assertEquals(5, calls.stream().filter(call -> call.endsWith("plate-4.stl")).count());
    }

    private SlicerService slicer(boolean failSecond, boolean oversizedSecond) throws Exception {
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                printf '%%s\\n' "$*" >> '%s'
                for input in "$@"; do :; done
                case "$input" in
                  *plate-1.stl) time=1m; grams=2; length=20 ;;
                  *plate-4.stl) %s
                    time=3m; grams=3; length=30 ;;
                  *) exit 99 ;;
                esac
                echo "; estimated printing time = $time" > plate_1.gcode
                echo "; filament used [g] = $grams" >> plate_1.gcode
                echo "; filament used [mm] = $length" >> plate_1.gcode
                """.formatted(tempDir.resolve("calls"), failSecond ? "echo 'Unknown slicing error'; exit 1;"
                : oversizedSecond ? "case \"$(cat machine.json)\" in *350x350*) ;; *) echo 'No object is fully inside the print volume'; exit 1 ;; esac;" : ""));
        assertTrue(executable.toFile().setExecutable(true));
        var mapper = new ObjectMapper();
        var profiles = mock(ProfileManager.class);
        var machine = mapper.createObjectNode();
        machine.putArray("nozzle_diameter").add("0.4");
        machine.putArray("printable_area").add("0x0").add("256x0").add("256x256").add("0x256");
        machine.put("printable_height", "256");
        when(profiles.getMergedProfile("machine", "machine")).thenAnswer(call -> machine.deepCopy());
        when(profiles.getMergedProfile("filament", "filament")).thenAnswer(call -> mapper.createObjectNode());
        when(profiles.getMergedProfile("process", "process")).thenAnswer(call -> mapper.createObjectNode());
        var slicer = spy(new SlicerService(executable.toString(), "unused-assimp", profiles,
                new GCodeParser(), mapper, mock(StepConversionService.class)));
        doReturn(Optional.of(new ModelDimensions(20, 10, 5))).when(slicer).inspectModelDimensions(any());
        return slicer;
    }
}

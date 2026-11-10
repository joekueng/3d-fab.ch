package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlicerPlacementRegressionTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @CsvSource({
            "10,SLICER_EXECUTION_FAILED", "256,SLICER_EXECUTION_FAILED",
            "256.000008,SLICER_EXECUTION_FAILED", "257,MODEL_OUT_OF_PRINT_VOLUME"
    })
    void classifiesPlacementFailureAgainstPhysicalVolumeAndPreservesRecoveryAttempts(double side, String code) throws Exception {
        Path attempts = tempDir.resolve("attempts.log");
        // Simulated CLI failure: no real slicing is needed for placement/error classification.
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                printf '%%s\\n' "$*" >> '%s'
                echo 'No object is fully inside the print volume'
                exit 1
                """.formatted(attempts));
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, side);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");
        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));
        assertEquals(code, failure.getCode());
        var commands = Files.readAllLines(attempts);
        assertEquals(4, commands.size());
        assertTrue(commands.getFirst().contains("--arrange 0"));
        assertTrue(commands.get(1).contains("--arrange 1"));
        assertTrue(commands.get(2).contains("--allow-rotations"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plate 1: found gcode in unprintable area of the printers! gcode_result->gcode_check_result.error_code = 4",
            "plate 1: found gcode in unprintable area of multi extruder printers!",
            "gcode path conflicts found between logo_badge and base"
    })
    void repositionsRejectedToolpathsAndKeepsSupportsEnabled(String errorLog) throws Exception {
        Path attempts = tempDir.resolve("attempts.log");
        Path settings = tempDir.resolve("settings.log");
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                printf '%%s\\n' "$*" >> '%s'
                cat process.json >> '%s'
                case " $* " in
                  *" --arrange 0 "*)
                    previous=''
                    for argument in "$@"; do
                      if [ "$previous" = '--logfile' ]; then
                        echo '%s' > "$argument"
                        break
                      fi
                      previous="$argument"
                    done
                    exit 154
                    ;;
                esac
                echo '; estimated printing time = 1h 46m 39s' > part.gcode
                echo '; filament used [g] = 50.44' >> part.gcode
                exit 0
                """.formatted(attempts, settings, errorLog));
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, 100);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");

        var stats = slicer.slice(input.toFile(), "machine", "filament", "process", null,
                Map.of("enable_support", "1"));

        assertEquals(6399, stats.printTimeSeconds());
        assertEquals(50.44, stats.filamentWeightGrams(), 0.001);
        var commands = Files.readAllLines(attempts);
        assertEquals(2, commands.size());
        assertTrue(commands.getFirst().contains("--arrange 0"));
        assertTrue(commands.getLast().contains("--arrange 1"));
        assertFalse(commands.getLast().contains("--orient"));
        assertTrue(commands.stream().noneMatch(command -> command.contains("--no-check")));
        // ObjectMapper writes compact JSON without a trailing newline.
        assertEquals("{\"enable_support\":\"1\"}".repeat(2), Files.readString(settings));
    }

    @ParameterizedTest
    @CsvSource({
            "100,SLICER_EXECUTION_FAILED", "257,MODEL_OUT_OF_PRINT_VOLUME"
    })
    void persistentUnprintableToolpathsRequireSplittingOnlyForOversizedGeometry(double side, String code) throws Exception {
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                echo 'found gcode in unprintable area of the printers!'
                exit 154
                """);
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, side);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");

        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));

        assertEquals(code, failure.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"part.gcode", "plate_1.gcode"})
    void rejectedGcodeCannotSupplyStatisticsWhenRetryProducesNoOutput(String outputName) throws Exception {
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                case " $* " in
                  *" --arrange 0 "*)
                    echo '; estimated printing time = 9h' > '%s'
                    echo '; filament used [g] = 900' >> '%s'
                    echo 'found gcode in unprintable area of the printers!'
                    exit 154
                    ;;
                esac
                exit 0
                """.formatted(outputName, outputName));
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, 100);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");

        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));

        assertEquals("SLICER_OUTPUT_MISSING", failure.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unknown slicing failure", "gcode path conflicts found between logo_badge and base"})
    void unrecoveredFailuresCannotProduceAQuoteFromRejectedOutput(String errorLog) throws Exception {
        Path attempts = tempDir.resolve("attempts.log");
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                printf '%%s\\n' "$*" >> '%s'
                echo '; estimated printing time = 9h' > part.gcode
                echo '; filament used [g] = 900' >> part.gcode
                echo '%s'
                exit 155
                """.formatted(attempts, errorLog));
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, 257);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");

        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));

        assertEquals("SLICER_EXECUTION_FAILED", failure.getCode());
        assertEquals(errorLog.startsWith("gcode path conflicts") ? 4 : 1, Files.readAllLines(attempts).size());
    }

    @Test
    void microscopicStlIsRejectedBeforeLaunchingASliceProcess() throws Exception {
        Path executable = tempDir.resolve("must-not-run");
        var slicer = configuredSlicer(executable, 0.1);
        Path input = Files.writeString(tempDir.resolve("microscopic.stl"), "synthetic input");
        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));
        assertEquals("MODEL_TOO_SMALL", failure.getCode());
        assertEquals("synthetic input", Files.readString(input));
    }

    @Test
    void successfulCliWithMissingWeightCannotProduceAReadyQuote() throws Exception {
        Path executable = Files.writeString(tempDir.resolve("slicer"), """
                #!/bin/sh
                echo '; estimated printing time = 3h 48m' > part.gcode
                echo '; filament used [g] = 0' >> part.gcode
                exit 0
                """);
        assertTrue(executable.toFile().setExecutable(true));
        var slicer = configuredSlicer(executable, 20);
        Path input = Files.writeString(tempDir.resolve("part.stl"), "synthetic input");
        var failure = assertThrows(ModelProcessingException.class,
                () -> slicer.slice(input.toFile(), "machine", "filament", "process", null, null));
        assertEquals("SLICER_STATS_UNAVAILABLE", failure.getCode());
    }

    private SlicerService configuredSlicer(Path executable, double side) throws Exception {
        var mapper = new ObjectMapper();
        var profiles = mock(ProfileManager.class);
        var machine = mapper.createObjectNode();
        machine.putArray("nozzle_diameter").add("0.4");
        machine.putArray("printable_area").add("0x0").add("256x0").add("256x256").add("0x256");
        machine.put("printable_height", "256");
        when(profiles.getMergedProfile("machine", "machine")).thenReturn(machine);
        when(profiles.getMergedProfile("filament", "filament")).thenReturn(mapper.createObjectNode());
        when(profiles.getMergedProfile("process", "process")).thenReturn(mapper.createObjectNode());
        var slicer = spy(new SlicerService(executable.toString(), "unused-assimp", profiles,
                new GCodeParser(), mapper, mock(StepConversionService.class)));
        doReturn(Optional.of(new ModelDimensions(side, side, side))).when(slicer).inspectModelDimensions(any());
        return slicer;
    }
}

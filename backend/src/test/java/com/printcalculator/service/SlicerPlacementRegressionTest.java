package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.model.ModelDimensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
                mock(GCodeParser.class), mapper, mock(StepConversionService.class)));
        doReturn(Optional.of(new ModelDimensions(side, side, side))).when(slicer).inspectModelDimensions(any());
        return slicer;
    }
}

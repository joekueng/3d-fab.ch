package com.printcalculator.service;

import com.printcalculator.exception.ModelProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StepConversionServiceTest {
    @TempDir Path tempDir;

    @Test
    void publishesOnlyACompleteStlAndPreservesTheOriginal() throws Exception {
        var converter = spy(new StepConversionService("unused-python", 2));
        var process = mock(Process.class);
        when(process.waitFor(2, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        var workDir = new AtomicReference<Path>();
        byte[] stl = ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN).putInt(80, 1).array();
        doAnswer(invocation -> {
            workDir.set(invocation.getArgument(3, Path.class));
            Files.write(invocation.getArgument(2, Path.class), stl);
            return process;
        }).when(converter).startConversion(any(), any(), any(), any());
        Path original = Files.writeString(tempDir.resolve("original.step"), "original CAD");
        Path output = tempDir.resolve("preview.stl");

        assertEquals(output, converter.convertToPersistentStl(original.toFile(), output));
        assertArrayEquals(stl, Files.readAllBytes(output));
        assertEquals("original CAD", Files.readString(original));
        assertFalse(Files.exists(workDir.get()));
    }

    @Test
    void invalidOutputDoesNotReplaceAnExistingPreview() throws Exception {
        var converter = spy(new StepConversionService("unused-python", 2));
        var process = mock(Process.class);
        when(process.waitFor(2, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(2, Path.class), "partial output");
            return process;
        }).when(converter).startConversion(any(), any(), any(), any());
        Path original = Files.writeString(tempDir.resolve("original.stp"), "original CAD");
        Path output = Files.writeString(tempDir.resolve("preview.stl"), "previous preview");

        var failure = assertThrows(ModelProcessingException.class,
                () -> converter.convertToPersistentStl(original.toFile(), output));
        assertEquals("MODEL_CONVERSION_FAILED", failure.getCode());
        assertEquals("previous preview", Files.readString(output));
        assertEquals("original CAD", Files.readString(original));
    }

    @Test
    void timeoutTerminatesTheProcessAndCleansItsWorkspace() throws Exception {
        var converter = spy(new StepConversionService("unused-python", 1));
        var process = mock(Process.class);
        when(process.waitFor(1, TimeUnit.SECONDS)).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        var workDir = new AtomicReference<Path>();
        doAnswer(invocation -> {
            workDir.set(invocation.getArgument(3, Path.class));
            return process;
        }).when(converter).startConversion(any(), any(), any(), any());
        Path original = Files.writeString(tempDir.resolve("original.step"), "original CAD");
        Path output = tempDir.resolve("preview.stl");

        assertThrows(ModelProcessingException.class,
                () -> converter.convertToPersistentStl(original.toFile(), output));
        verify(process).destroyForcibly();
        assertFalse(Files.exists(workDir.get()));
        assertFalse(Files.exists(output));
        assertTrue(Files.exists(original));
    }

    @Test
    void missingConverterFailsWithoutPublishingOrRemovingFiles() throws Exception {
        var converter = new StepConversionService(tempDir.resolve("missing-python").toString(), 1);
        Path original = Files.writeString(tempDir.resolve("original.stp"), "original CAD");
        Path output = tempDir.resolve("preview.stl");
        assertThrows(ModelProcessingException.class,
                () -> converter.convertToPersistentStl(original.toFile(), output));
        assertTrue(Files.exists(original));
        assertFalse(Files.exists(output));
    }
}

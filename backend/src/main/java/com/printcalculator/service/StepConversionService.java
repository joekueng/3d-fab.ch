package com.printcalculator.service;

import com.printcalculator.exception.ModelProcessingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class StepConversionService {
    private static final Logger logger = Logger.getLogger(StepConversionService.class.getName());
    private final String pythonPath;
    private final long timeoutSeconds;

    public StepConversionService(
            @Value("${model.step.python-path:/opt/step-converter/bin/python}") String pythonPath,
            @Value("${model.step.timeout-seconds:120}") long timeoutSeconds) {
        this.pythonPath = SlicerFileSupport.normalizeExecutablePath(pythonPath, "model.step.python-path");
        if (timeoutSeconds < 1) throw new IllegalArgumentException("STEP conversion timeout must be positive");
        this.timeoutSeconds = timeoutSeconds;
    }

    public Path convertToPersistentStl(File inputModel, Path destinationStl) throws IOException {
        Path tempDir = Files.createTempDirectory("step_convert_");
        Process process = null;
        try {
            Path script = tempDir.resolve("step_to_stl.py");
            try (var source = new ClassPathResource("model-conversion/step_to_stl.py").getInputStream()) {
                Files.copy(source, script);
            }
            Path converted = tempDir.resolve("converted.stl");
            process = startConversion(script, inputModel.toPath().toAbsolutePath(), converted, tempDir);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("STEP conversion timed out");
            }
            if (process.exitValue() != 0) {
                Path log = tempDir.resolve("conversion.log");
                try (var input = Files.newInputStream(log)) {
                    logger.warning("STEP converter failed: " + new String(input.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8));
                }
                throw new IOException("STEP converter exited with code " + process.exitValue());
            }
            validateBinaryStl(converted);
            Files.createDirectories(destinationStl.toAbsolutePath().getParent());
            Files.copy(converted, destinationStl, StandardCopyOption.REPLACE_EXISTING);
            return destinationStl;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ModelProcessingException("MODEL_CONVERSION_FAILED", "STEP conversion was interrupted.", failure);
        } catch (IOException failure) {
            throw new ModelProcessingException("MODEL_CONVERSION_FAILED",
                    "Unable to prepare this STEP model. Please request a consultation.", failure);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
            }
            SlicerFileSupport.deleteRecursively(tempDir, logger);
        }
    }

    Process startConversion(Path script, Path input, Path output, Path workingDirectory) throws IOException {
        var builder = new ProcessBuilder(List.of(pythonPath, "-I", script.toString(),
                input.toString(), output.toString()));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(workingDirectory.resolve("conversion.log").toFile());
        return builder.start();
    }

    private void validateBinaryStl(Path stl) throws IOException {
        try (var input = Files.newInputStream(stl)) {
            byte[] header = input.readNBytes(84);
            if (header.length != 84) throw new IOException("STEP conversion produced an empty STL");
            long triangles = Integer.toUnsignedLong(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(80));
            if (triangles == 0 || Files.size(stl) != 84L + triangles * 50L) {
                throw new IOException("STEP conversion produced an invalid STL");
            }
        }
    }
}

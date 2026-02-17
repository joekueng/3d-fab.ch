package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.model.PrintStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class SlicerService {

    private static final Logger logger = Logger.getLogger(SlicerService.class.getName());

    private final String slicerPath;
    private final ProfileManager profileManager;
    private final GCodeParser gCodeParser;
    private final ObjectMapper mapper;

    public SlicerService(
            @Value("${slicer.path}") String slicerPath,
            ProfileManager profileManager,
            GCodeParser gCodeParser,
            ObjectMapper mapper) {
        this.slicerPath = slicerPath;
        this.profileManager = profileManager;
        this.gCodeParser = gCodeParser;
        this.mapper = mapper;
    }

    public PrintStats slice(File inputStl, String machineName, String filamentName, String processName,
                            Map<String, String> machineOverrides, Map<String, String> processOverrides) throws IOException {
        // 1. Prepare Profiles
        ObjectNode machineProfile = profileManager.getMergedProfile(machineName, "machine");
        ObjectNode filamentProfile = profileManager.getMergedProfile(filamentName, "filament");
        ObjectNode processProfile = profileManager.getMergedProfile(processName, "process");

        // Apply Overrides
        if (machineOverrides != null) {
            machineOverrides.forEach(machineProfile::put);
        }
        if (processOverrides != null) {
            processOverrides.forEach(processProfile::put);
        }

        // 2. Create Temp Dir
        Path tempDir = Files.createTempDirectory("slicer_job_");
        try {
            File mFile = tempDir.resolve("machine.json").toFile();
            File fFile = tempDir.resolve("filament.json").toFile();
            File pFile = tempDir.resolve("process.json").toFile();

            mapper.writeValue(mFile, machineProfile);
            mapper.writeValue(fFile, filamentProfile);
            mapper.writeValue(pFile, processProfile);

            // 3. Build Command
            // --load-settings "machine.json;process.json" --load-filaments "filament.json"
            List<String> command = new ArrayList<>();
            command.add(slicerPath);
            
            // Load machine settings
            command.add("--load-settings");
            command.add(mFile.getAbsolutePath());
            
            // Load process settings
            command.add("--load-settings");
            command.add(pFile.getAbsolutePath());
            command.add("--load-filaments");
            command.add(fFile.getAbsolutePath());
            command.add("--ensure-on-bed");
            // Single-model jobs do not need arrange; it can fail on near-limit models.
            command.add("--slice");
            command.add("0"); // slice plate 0
            command.add("--outputdir");
            command.add(tempDir.toAbsolutePath().toString());
            // Need to handle Mac structure for console if needed? 
            // Usually the binary at Contents/MacOS/OrcaSlicer works fine as console app.
            
            command.add(inputStl.getAbsolutePath());

            logger.info("Executing Slicer: " + String.join(" ", command));

            // 4. Run Process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            Path slicerLogPath = tempDir.resolve("orcaslicer.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(slicerLogPath.toFile());
            
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Slicer timed out");
            }
            
            if (process.exitValue() != 0) {
                String error = "";
                if (Files.exists(slicerLogPath)) {
                    error = Files.readString(slicerLogPath, StandardCharsets.UTF_8);
                }
                throw new IOException("Slicer failed with exit code " + process.exitValue() + ": " + error);
            }

            // 5. Find Output GCode
            // Usually [basename].gcode or plate_1.gcode
            String basename = inputStl.getName();
            if (basename.toLowerCase().endsWith(".stl")) {
                basename = basename.substring(0, basename.length() - 4);
            }
            
            File gcodeFile = tempDir.resolve(basename + ".gcode").toFile();
            if (!gcodeFile.exists()) {
                // Try plate_1.gcode fallback
                File alt = tempDir.resolve("plate_1.gcode").toFile();
                if (alt.exists()) {
                    gcodeFile = alt;
                } else {
                     throw new IOException("GCode output not found in " + tempDir);
                }
            }

            // 6. Parse Results
            return gCodeParser.parse(gcodeFile);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during slicing", e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    logger.warning("Failed to delete temp path " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warning("Failed to walk temp directory " + path + ": " + e.getMessage());
        }
    }
}

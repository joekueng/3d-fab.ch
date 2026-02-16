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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
        
        ObjectNode machineProfile = profileManager.getMergedProfile(machineName, "machine");
        ObjectNode filamentProfile = profileManager.getMergedProfile(filamentName, "filament");
        ObjectNode processProfile = profileManager.getMergedProfile(processName, "process");

        if (machineOverrides != null) machineOverrides.forEach(machineProfile::put);
        if (processOverrides != null) processOverrides.forEach(processProfile::put);
        
        // Mantengo questa rimozione perché risolveva un errore specifico di triangolazione nei log
        machineProfile.remove("bed_exclude_area");

        Path baseTempPath = Paths.get("/app/temp");
        if (!Files.exists(baseTempPath)) Files.createDirectories(baseTempPath);
        Path tempDir = Files.createTempDirectory(baseTempPath, "job_");
        
        try {
            File localStl = tempDir.resolve("input.stl").toFile();
            Files.copy(inputStl.toPath(), localStl.toPath());

            File mFile = tempDir.resolve("machine.json").toFile();
            File fFile = tempDir.resolve("filament.json").toFile();
            File pFile = tempDir.resolve("process.json").toFile();

            mapper.writeValue(mFile, machineProfile);
            mapper.writeValue(fFile, filamentProfile);
            mapper.writeValue(pFile, processProfile);

            List<String> command = new ArrayList<>();
            command.add(slicerPath); 
            command.add("--slice");
            command.add("1"); 
            command.add("--outputdir");
            command.add(tempDir.toAbsolutePath().toString());
            command.add("--load-settings");
            command.add(mFile.getAbsolutePath());
            command.add("--load-settings");
            command.add(pFile.getAbsolutePath());
            command.add("--load-filaments");
            command.add(fFile.getAbsolutePath());
            command.add(localStl.getAbsolutePath());

            logger.info("Executing Slicer: " + String.join(" ", command));

            runSlicerCommand(command, tempDir);

            // Find any .gcode file
            try (Stream<Path> s = Files.list(tempDir)) {
                Optional<Path> found = s.filter(p -> p.toString().endsWith(".gcode")).findFirst();
                if (found.isPresent()) return gCodeParser.parse(found.get().toFile());
                else throw new IOException("No GCode found in " + tempDir);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    protected void runSlicerCommand(List<String> command, Path tempDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.environment().put("HOME", "/tmp");
        pb.environment().put("QT_QPA_PLATFORM", "offscreen");
        
        Process process = pb.start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroy();
            throw new IOException("Slicer timeout");
        }

        if (process.exitValue() != 0) {
            String out = new String(process.getInputStream().readAllBytes());
            String err = new String(process.getErrorStream().readAllBytes());
            throw new IOException("Slicer failed with exit code " + process.exitValue() + "\nERR: " + err + "\nOUT: " + out);
        }
    }
}

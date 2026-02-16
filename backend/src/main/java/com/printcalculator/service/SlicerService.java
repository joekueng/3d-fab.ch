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
        
        // Evitiamo solo asset grafici opzionali che potrebbero richiedere file esterni
        if (machineProfile.has("bed_custom_model")) machineProfile.put("bed_custom_model", "");
        if (machineProfile.has("bed_custom_texture")) machineProfile.put("bed_custom_texture", "");
        machineProfile.remove("thumbnail");

        // OrcaSlicer si aspetta un poligono valido per bed_exclude_area.
        // Alcuni profili BBL la sovrascrivono con [] e causano "Unable to create exclude triangles".
        // Usiamo un quadrato minimo per rendere il poligono valido senza impattare la superficie utile.
        if (!machineProfile.has("bed_exclude_area")
                || !machineProfile.get("bed_exclude_area").isArray()
                || machineProfile.get("bed_exclude_area").size() < 4) {
            machineProfile.putArray("bed_exclude_area")
                    .add("0x0")
                    .add("1x0")
                    .add("1x1")
                    .add("0x1");
        }

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
            
            command.add("--load-settings");
            command.add(mFile.getAbsolutePath());
            command.add("--load-settings");
            command.add(pFile.getAbsolutePath());
            command.add("--load-filaments");
            command.add(fFile.getAbsolutePath());
            
            command.add("--outputdir");
            command.add(tempDir.toAbsolutePath().toString());
            
            // Forza il posizionamento automatico: indispensabile per pezzi grandi che potrebbero
            // essere salvati con coordinate fuori dal centro piatto
            command.add("--arrange");
            command.add("1"); 
            command.add("--ensure-on-bed");
            
            command.add("--slice");
            command.add("0");

            command.add(localStl.getAbsolutePath());

            logger.info("Executing Slicer for " + machineName + " on: " + localStl.getAbsolutePath());

            runSlicerCommand(command, tempDir);

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
        
        Map<String, String> env = pb.environment();
        env.put("HOME", "/tmp");
        env.put("QT_QPA_PLATFORM", "offscreen");
        
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

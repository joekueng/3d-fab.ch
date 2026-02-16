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
        
        // Log version once for diagnostics
        try { runVersionCheck(); } catch (Exception e) {}

        ObjectNode machineProfile = profileManager.getMergedProfile(machineName, "machine");
        ObjectNode filamentProfile = profileManager.getMergedProfile(filamentName, "filament");
        ObjectNode processProfile = profileManager.getMergedProfile(processName, "process");

        if (machineOverrides != null) machineOverrides.forEach(machineProfile::put);
        if (processOverrides != null) processOverrides.forEach(processProfile::put);
        
        // Pulizia radicale per rendere la macchina "anonima" ed evitare crash geometrici su zone di esclusione
        makeMachineGeneric(machineProfile);

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
            
            // Ordine ottimizzato per OrcaSlicer 1.9+
            command.add("--load-settings");
            command.add(mFile.getAbsolutePath());
            command.add("--load-settings");
            command.add(pFile.getAbsolutePath());
            command.add("--load-filaments");
            command.add(fFile.getAbsolutePath());
            
            command.add("--outputdir");
            command.add(tempDir.toAbsolutePath().toString());
            
            command.add("--arrange");
            command.add("1");
            command.add("--ensure-on-bed");
            
            command.add("--slice");
            command.add("0");

            command.add(localStl.getAbsolutePath());

            logger.info("Executing Slicer on file: " + localStl.getAbsolutePath() + " (Size: " + localStl.length() + " bytes)");

            runSlicerCommand(command, tempDir);

            // Cerca il file G-code prodotto
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

    private void makeMachineGeneric(ObjectNode profile) {
        // Forza l'identità della stampante per usare i parametri di accelerazione/velocità della A1
        profile.put("printer_model", "Bambu Lab A1");
        
        // Rimuove l'ereditarietà e gli ID per evitare che lo slicer cerchi di ricaricare asset di sistema (mesh/texture)
        profile.remove("inherits");
        profile.remove("setting_id");
        profile.remove("printer_settings_id");
        
        // Rimuove zone di esclusione e modelli complessi che richiedono calcoli grafici pesanti (CAUSA CRASH IN HEADLESS)
        profile.remove("bed_exclude_area");
        profile.remove("head_wrap_detect_zone");
        profile.remove("bed_custom_model");
        profile.remove("bed_custom_texture");
        profile.remove("thumbnail");
        profile.remove("thumbnails");

        // Forza un'area di stampa standard 256x256x256 (Bambu A1)
        try {
            profile.set("printable_area", mapper.readTree("[\"0x0\",\"256x0\",\"256x256\",\"0x256\"]"));
            profile.put("printable_height", "256");
        } catch (Exception ignored) {}
    }

    private void runVersionCheck() throws IOException, InterruptedException {
        Process p = new ProcessBuilder(slicerPath, "--version").start();
        p.waitFor();
        String ver = new String(p.getInputStream().readAllBytes()).trim();
        logger.info("OrcaSlicer Version on server: " + ver);
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

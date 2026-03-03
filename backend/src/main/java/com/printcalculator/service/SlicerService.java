package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.model.ModelDimensions;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SlicerService {

    private static final Logger logger = Logger.getLogger(SlicerService.class.getName());
    private static final Pattern SIZE_X_PATTERN = Pattern.compile("(?m)^\\s*size_x\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Y_PATTERN = Pattern.compile("(?m)^\\s*size_y\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SIZE_Z_PATTERN = Pattern.compile("(?m)^\\s*size_z\\s*=\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*$");

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

        logger.info("Slicer profiles: machine='" + machineName + "', filament='" + filamentName + "', process='" + processName + "'");
        logger.info("Machine limits: printable_area=" + machineProfile.path("printable_area")
                + ", printable_height=" + machineProfile.path("printable_height")
                + ", bed_exclude_area=" + machineProfile.path("bed_exclude_area")
                + ", head_wrap_detect_zone=" + machineProfile.path("head_wrap_detect_zone"));

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

            String basename = inputStl.getName();
            if (basename.toLowerCase().endsWith(".stl")) {
                basename = basename.substring(0, basename.length() - 4);
            }
            Path slicerLogPath = tempDir.resolve("orcaslicer.log");

            // 3. Run slicer. Retry with arrange only for out-of-volume style failures.
            for (boolean useArrange : new boolean[]{false, true}) {
                List<String> command = new ArrayList<>();
                command.add(slicerPath);
                command.add("--load-settings");
                command.add(mFile.getAbsolutePath());
                command.add("--load-settings");
                command.add(pFile.getAbsolutePath());
                command.add("--load-filaments");
                command.add(fFile.getAbsolutePath());
                command.add("--ensure-on-bed");
                if (useArrange) {
                    command.add("--arrange");
                    command.add("1");
                }
                command.add("--slice");
                command.add("0");
                command.add("--outputdir");
                command.add(tempDir.toAbsolutePath().toString());
                command.add(inputStl.getAbsolutePath());

                logger.info("Executing Slicer" + (useArrange ? " (retry with arrange)" : "") + ": " + String.join(" ", command));

                Files.deleteIfExists(slicerLogPath);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(tempDir.toFile());
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
                    if (!useArrange && isOutOfVolumeError(error)) {
                        logger.warning("Slicer reported model out of printable area, retrying with arrange.");
                        continue;
                    }
                    throw new IOException("Slicer failed with exit code " + process.exitValue() + ": " + error);
                }

                File gcodeFile = tempDir.resolve(basename + ".gcode").toFile();
                if (!gcodeFile.exists()) {
                    File alt = tempDir.resolve("plate_1.gcode").toFile();
                    if (alt.exists()) {
                        gcodeFile = alt;
                    } else {
                        throw new IOException("GCode output not found in " + tempDir);
                    }
                }

                return gCodeParser.parse(gcodeFile);
            }

            throw new IOException("Slicer failed after retry");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during slicing", e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public Optional<ModelDimensions> inspectModelDimensions(File inputModel) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("slicer_info_");
            Path infoLogPath = tempDir.resolve("orcaslicer-info.log");

            List<String> command = new ArrayList<>();
            command.add(slicerPath);
            command.add("--info");
            command.add(inputModel.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(infoLogPath.toFile());

            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                logger.warning("Model info extraction timed out for " + inputModel.getName());
                return Optional.empty();
            }

            String output = Files.exists(infoLogPath)
                    ? Files.readString(infoLogPath, StandardCharsets.UTF_8)
                    : "";

            if (process.exitValue() != 0) {
                logger.warning("OrcaSlicer --info failed (exit " + process.exitValue() + ") for "
                        + inputModel.getName() + ": " + output);
                return Optional.empty();
            }

            Optional<ModelDimensions> parsed = parseModelDimensionsFromInfoOutput(output);
            if (parsed.isEmpty()) {
                logger.warning("Could not parse size_x/size_y/size_z from OrcaSlicer --info output for "
                        + inputModel.getName() + ": " + output);
            }
            return parsed;
        } catch (Exception e) {
            logger.warning("Failed to inspect model dimensions for " + inputModel.getName() + ": " + e.getMessage());
            return Optional.empty();
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    static Optional<ModelDimensions> parseModelDimensionsFromInfoOutput(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }

        Double x = extractDouble(SIZE_X_PATTERN, output);
        Double y = extractDouble(SIZE_Y_PATTERN, output);
        Double z = extractDouble(SIZE_Z_PATTERN, output);

        if (x == null || y == null || z == null) {
            return Optional.empty();
        }

        if (x <= 0 || y <= 0 || z <= 0) {
            return Optional.empty();
        }

        return Optional.of(new ModelDimensions(x, y, z));
    }

    private static Double extractDouble(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
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

    private boolean isOutOfVolumeError(String errorLog) {
        if (errorLog == null || errorLog.isBlank()) {
            return false;
        }

        String normalized = errorLog.toLowerCase();
        return normalized.contains("nothing to be sliced")
                || normalized.contains("no object is fully inside the print volume")
                || normalized.contains("calc_exclude_triangles");
    }
}

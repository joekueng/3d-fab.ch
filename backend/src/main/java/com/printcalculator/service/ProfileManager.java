package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfileManager {

    private static final Logger logger = Logger.getLogger(ProfileManager.class.getName());
    private static final Pattern LAYER_MM_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)mm\\b", Pattern.CASE_INSENSITIVE);
    private final String profilesRoot;
    private final Path resolvedProfilesRoot;
    private final ObjectMapper mapper;

    private final Map<String, String> profileAliases;
    private volatile List<ProcessProfileMeta> cachedProcessProfiles;

    public ProfileManager(@Value("${profiles.root:profiles}") String profilesRoot, ObjectMapper mapper) {
        this.profilesRoot = profilesRoot;
        this.mapper = mapper;
        this.profileAliases = new HashMap<>();
        initializeAliases();
        this.resolvedProfilesRoot = resolveProfilesRoot(profilesRoot);
        logger.info("Profiles root configured as '" + this.profilesRoot + "', resolved to '" + this.resolvedProfilesRoot + "'");
    }

    private void initializeAliases() {
        // Machine Aliases
        profileAliases.put("bambu_a1", "Bambu Lab A1 0.4 nozzle");
        profileAliases.put("bambu_p2s", "Bambu Lab P2S 0.4 nozzle");
        
        // Material Aliases
        profileAliases.put("pla_basic", "Bambu PLA Basic @BBL A1");
        profileAliases.put("pla_tough", "Bambu PLA Tough @BBL A1");
        profileAliases.put("petg_basic", "Bambu PETG Basic @BBL A1");
        profileAliases.put("tpu_95a", "Bambu TPU 95A @BBL A1");
        
        // Quality/Process Aliases
        profileAliases.put("draft", "0.24mm Draft @BBL A1");
        profileAliases.put("standard", "0.20mm Standard @BBL A1"); // or 0.20mm Standard @BBL A1
        profileAliases.put("extra_fine", "0.08mm High Quality @BBL A1");
        
        // Additional aliases from error logs
        profileAliases.put("Bambu_Process_0.20_Standard", "0.20mm Standard @BBL A1");
    }

    public ObjectNode getMergedProfile(String profileName, String type) throws IOException {
        Path profilePath = findProfileFile(profileName, type);
        if (profilePath == null) {
            throw new IOException("Profile not found: " + profileName + " (root=" + resolvedProfilesRoot + ")");
        }
        logger.info("Resolved " + type + " profile '" + profileName + "' -> " + profilePath);
        return resolveInheritance(profilePath);
    }

    public List<BigDecimal> findCompatibleProcessLayers(String machineProfileName) {
        if (machineProfileName == null || machineProfileName.isBlank()) {
            return List.of();
        }

        Set<BigDecimal> layers = new LinkedHashSet<>();
        for (ProcessProfileMeta meta : getOrLoadProcessProfiles()) {
            if (meta.compatiblePrinters().contains(machineProfileName) && meta.layerHeightMm() != null) {
                layers.add(meta.layerHeightMm());
            }
        }
        if (layers.isEmpty()) {
            return List.of();
        }

        List<BigDecimal> sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    public Optional<String> findCompatibleProcessProfileName(String machineProfileName,
                                                             BigDecimal layerHeightMm,
                                                             String qualityHint) {
        if (machineProfileName == null || machineProfileName.isBlank() || layerHeightMm == null) {
            return Optional.empty();
        }

        BigDecimal normalizedLayer = layerHeightMm.setScale(3, RoundingMode.HALF_UP);
        String normalizedQuality = String.valueOf(qualityHint == null ? "" : qualityHint)
                .trim()
                .toLowerCase(Locale.ROOT);

        List<ProcessProfileMeta> candidates = new ArrayList<>();
        for (ProcessProfileMeta meta : getOrLoadProcessProfiles()) {
            if (!meta.compatiblePrinters().contains(machineProfileName)) {
                continue;
            }
            if (meta.layerHeightMm() == null || meta.layerHeightMm().compareTo(normalizedLayer) != 0) {
                continue;
            }
            candidates.add(meta);
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator
                .comparingInt((ProcessProfileMeta meta) -> scoreProcessForQuality(meta.name(), normalizedQuality))
                .reversed()
                .thenComparing(ProcessProfileMeta::name, String.CASE_INSENSITIVE_ORDER));

        return Optional.ofNullable(candidates.get(0).name());
    }

    private Path findProfileFile(String name, String type) {
        if (!Files.isDirectory(resolvedProfilesRoot)) {
            logger.severe("Profiles root does not exist or is not a directory: " + resolvedProfilesRoot);
            return null;
        }

        // Check aliases first
        String resolvedName = profileAliases.getOrDefault(name, name);

        // Look for name.json under the expected type directory first to avoid
        // collisions across vendors/profile families with same filename.
        String filename = toJsonFilename(resolvedName);

        try (Stream<Path> stream = Files.walk(resolvedProfilesRoot)) {
            List<Path> candidates = stream
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .sorted()
                    .toList();

            if (candidates.isEmpty()) {
                return null;
            }

            if (type != null && !type.isBlank() && !"any".equalsIgnoreCase(type)) {
                Optional<Path> typed = candidates.stream()
                        .filter(p -> pathContainsSegment(p, type))
                        .findFirst();
                if (typed.isPresent()) {
                    return typed.get();
                }
            }

            return candidates.get(0);
        } catch (IOException e) {
            logger.severe("Error searching for profile: " + e.getMessage());
            return null;
        }
    }

    private Path resolveProfilesRoot(String configuredRoot) {
        Set<Path> candidates = new LinkedHashSet<>();
        Path cwd = Paths.get("").toAbsolutePath().normalize();

        if (configuredRoot != null && !configuredRoot.isBlank()) {
            Path configured = Paths.get(configuredRoot);
            candidates.add(configured.toAbsolutePath().normalize());
            if (!configured.isAbsolute()) {
                candidates.add(cwd.resolve(configuredRoot).normalize());
            }
        }

        candidates.add(cwd.resolve("profiles").normalize());
        candidates.add(cwd.resolve("backend/profiles").normalize());
        candidates.add(Paths.get("/app/profiles").toAbsolutePath().normalize());

        List<String> checkedPaths = new ArrayList<>();
        for (Path candidate : candidates) {
            checkedPaths.add(candidate.toString());
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        logger.warning("No profiles directory found. Checked: " + String.join(", ", checkedPaths));
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Paths.get(configuredRoot).toAbsolutePath().normalize();
        }
        return cwd.resolve("profiles").normalize();
    }

    private ObjectNode resolveInheritance(Path currentPath) throws IOException {
        // 1. Load current
        JsonNode currentNode = mapper.readTree(currentPath.toFile());
        
        // 2. Check inherits
        if (currentNode.has("inherits")) {
            String parentName = currentNode.get("inherits").asText();
            // Try local directory first with explicit .json filename.
            String parentFilename = toJsonFilename(parentName);
            Path parentPath = currentPath.getParent().resolve(parentFilename);
            if (!Files.exists(parentPath)) {
                // Fallback to the same profile type directory before global.
                String inferredType = inferTypeFromPath(currentPath);
                parentPath = findProfileFile(parentName, inferredType);
            }
            if (parentPath == null || !Files.exists(parentPath)) {
                parentPath = findProfileFile(parentName, "any");
            }

            if (parentPath != null && Files.exists(parentPath)) {
                logger.info("Resolved inherits '" + parentName + "' for " + currentPath + " -> " + parentPath);
                // Recursive call
                ObjectNode parentNode = resolveInheritance(parentPath);
                // Merge current into parent (child overrides parent)
                merge(parentNode, (ObjectNode) currentNode);
                // Remove "inherits" field
                parentNode.remove("inherits");
                return parentNode;
            } else {
                 logger.warning("Inherited profile not found: " + parentName + " for " + currentPath);
            }
        }

        if (currentNode instanceof ObjectNode) {
            return (ObjectNode) currentNode;
        } else {
             // Should verify it is an object
             return (ObjectNode) currentNode;
        }
    }

    // Shallow merge suitable for OrcaSlicer profiles
    private void merge(ObjectNode mainNode, ObjectNode updateNode) {
        Iterator<String> fieldNames = updateNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = updateNode.get(fieldName);
            // Replace standard fields
            mainNode.set(fieldName, jsonNode);
        }
    }

    private String toJsonFilename(String name) {
        return name.endsWith(".json") ? name : name + ".json";
    }

    private boolean pathContainsSegment(Path path, String segment) {
        String normalized = path.toString().replace('\\', '/');
        String needle = "/" + segment + "/";
        return normalized.contains(needle);
    }

    private String inferTypeFromPath(Path path) {
        if (path == null) {
            return "any";
        }
        if (pathContainsSegment(path, "machine")) {
            return "machine";
        }
        if (pathContainsSegment(path, "process")) {
            return "process";
        }
        if (pathContainsSegment(path, "filament")) {
            return "filament";
        }
        return "any";
    }

    private List<ProcessProfileMeta> getOrLoadProcessProfiles() {
        List<ProcessProfileMeta> cached = cachedProcessProfiles;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (cachedProcessProfiles != null) {
                return cachedProcessProfiles;
            }

            List<ProcessProfileMeta> loaded = new ArrayList<>();
            if (!Files.isDirectory(resolvedProfilesRoot)) {
                cachedProcessProfiles = Collections.emptyList();
                return cachedProcessProfiles;
            }

            try (Stream<Path> stream = Files.walk(resolvedProfilesRoot)) {
                List<Path> processFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .filter(path -> pathContainsSegment(path, "process"))
                        .sorted()
                        .toList();

                for (Path processFile : processFiles) {
                    try {
                        JsonNode node = mapper.readTree(processFile.toFile());
                        if (!"process".equalsIgnoreCase(node.path("type").asText())) {
                            continue;
                        }

                        String name = node.path("name").asText("");
                        if (name.isBlank()) {
                            continue;
                        }

                        BigDecimal layer = extractLayerHeightFromProfileName(name);
                        if (layer == null) {
                            continue;
                        }

                        Set<String> compatiblePrinters = new LinkedHashSet<>();
                        JsonNode compatibleNode = node.path("compatible_printers");
                        if (compatibleNode.isArray()) {
                            compatibleNode.forEach(value -> {
                                String printer = value.asText("").trim();
                                if (!printer.isBlank()) {
                                    compatiblePrinters.add(printer);
                                }
                            });
                        }

                        if (compatiblePrinters.isEmpty()) {
                            continue;
                        }

                        loaded.add(new ProcessProfileMeta(name, layer, compatiblePrinters));
                    } catch (Exception ignored) {
                        // Ignore malformed or non-process JSON files.
                    }
                }
            } catch (IOException e) {
                logger.warning("Failed to scan process profiles: " + e.getMessage());
            }

            cachedProcessProfiles = List.copyOf(loaded);
            return cachedProcessProfiles;
        }
    }

    private BigDecimal extractLayerHeightFromProfileName(String profileName) {
        if (profileName == null) {
            return null;
        }
        Matcher matcher = LAYER_MM_PATTERN.matcher(profileName.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1)).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int scoreProcessForQuality(String processName, String qualityHint) {
        String normalizedName = String.valueOf(processName == null ? "" : processName)
                .toLowerCase(Locale.ROOT);
        if (qualityHint == null || qualityHint.isBlank()) {
            return 0;
        }

        return switch (qualityHint) {
            case "draft" -> {
                if (normalizedName.contains("extra draft")) yield 30;
                if (normalizedName.contains("draft")) yield 20;
                if (normalizedName.contains("standard")) yield 10;
                yield 0;
            }
            case "extra_fine", "high", "high_definition" -> {
                if (normalizedName.contains("extra fine")) yield 30;
                if (normalizedName.contains("high quality")) yield 25;
                if (normalizedName.contains("fine")) yield 20;
                if (normalizedName.contains("standard")) yield 5;
                yield 0;
            }
            default -> {
                if (normalizedName.contains("standard")) yield 30;
                if (normalizedName.contains("optimal")) yield 25;
                if (normalizedName.contains("strength")) yield 20;
                if (normalizedName.contains("high quality")) yield 10;
                if (normalizedName.contains("draft")) yield 5;
                yield 0;
            }
        };
    }

    private record ProcessProfileMeta(String name, BigDecimal layerHeightMm, Set<String> compatiblePrinters) {
    }
}

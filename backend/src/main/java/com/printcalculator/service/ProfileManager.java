package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ProfileManager {

    private static final Logger logger = Logger.getLogger(ProfileManager.class.getName());
    private final String profilesRoot;
    private final Path resolvedProfilesRoot;
    private final ObjectMapper mapper;

    private final Map<String, String> profileAliases;

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
        
        // Material Aliases
        profileAliases.put("pla_basic", "Bambu PLA Basic @BBL A1");
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
}

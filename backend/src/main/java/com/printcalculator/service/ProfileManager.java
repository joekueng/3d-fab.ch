package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
public class ProfileManager {

    private static final Logger logger = Logger.getLogger(ProfileManager.class.getName());
    private final String profilesRoot;
    private final ObjectMapper mapper;

    public ProfileManager(@Value("${profiles.root:profiles}") String profilesRoot, ObjectMapper mapper) {
        this.profilesRoot = profilesRoot;
        this.mapper = mapper;
    }

    public ObjectNode getMergedProfile(String profileName, String type) throws IOException {
        Path profilePath = findProfileFile(profileName, type);
        if (profilePath == null) {
            throw new IOException("Profile not found: " + profileName);
        }
        return resolveInheritance(profilePath);
    }

    private Path findProfileFile(String name, String type) {
        // Simple search: look for name.json in the profiles_root recursively
        // Type could be "machine", "process", "filament" to narrow down, but for now global search
        String filename = name.endsWith(".json") ? name : name + ".json";
        
        try (Stream<Path> stream = Files.walk(Paths.get(profilesRoot))) {
            Optional<Path> found = stream
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .findFirst();
            return found.orElse(null);
        } catch (IOException e) {
            logger.severe("Error searching for profile: " + e.getMessage());
            return null;
        }
    }

    private ObjectNode resolveInheritance(Path currentPath) throws IOException {
        // 1. Load current
        JsonNode currentNode = mapper.readTree(currentPath.toFile());
        
        // 2. Check inherits
        if (currentNode.has("inherits")) {
            String parentName = currentNode.get("inherits").asText();
            // Try to find parent in same directory or standard search
            Path parentPath = currentPath.getParent().resolve(parentName);
            if (!Files.exists(parentPath)) {
                // If not in same dir, search globally
                parentPath = findProfileFile(parentName, "any");
            }

            if (parentPath != null && Files.exists(parentPath)) {
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
}

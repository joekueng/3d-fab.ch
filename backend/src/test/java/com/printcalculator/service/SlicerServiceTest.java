package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.model.PrintStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class SlicerServiceTest {

    @Mock
    private ProfileManager profileManager;
    
    @Mock
    private GCodeParser gCodeParser;

    private ObjectMapper mapper = new ObjectMapper();

    private SlicerService slicerService;

    @TempDir
    Path tempDir;

    // Captured execution details
    private List<String> lastCommand;
    private Path lastTempDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        // Subclass to override runSlicerCommand
        slicerService = new SlicerService("orca-slicer", profileManager, gCodeParser, mapper) {
            @Override
            protected void runSlicerCommand(List<String> command, Path tempDir) throws IOException {
                lastCommand = command;
                lastTempDir = tempDir;
                // Don't run actual process.
                // Simulate GCode output creation for the parser to find?
                // Or just let it fail at parser step since we only care about JSON generation here?
                // For a full test, we should create a dummy GCode file.
                
                File stl = new File(command.get(command.size() - 1));
                String basename = stl.getName().replace(".stl", "");
                Files.createFile(tempDir.resolve(basename + ".gcode"));
            }
        };
        
        // Mock Profile Responses
        ObjectNode emptyNode = mapper.createObjectNode();
        when(profileManager.getMergedProfile(anyString(), eq("machine"))).thenReturn(emptyNode.deepCopy());
        when(profileManager.getMergedProfile(anyString(), eq("filament"))).thenReturn(emptyNode.deepCopy());
        when(profileManager.getMergedProfile(anyString(), eq("process"))).thenReturn(emptyNode.deepCopy());
        
        // Mock Parser
        when(gCodeParser.parse(any(File.class))).thenReturn(new PrintStats(100, "1m 40s", 10.5, 1000));
    }

    @Test
    void testSlice_WithDefaults_ShouldGenerateConfig() throws IOException {
        File dummyStl = tempDir.resolve("test.stl").toFile();
        Files.createFile(dummyStl.toPath());

        slicerService.slice(dummyStl, "Bambu A1", "PLA", "Standard", null, null);

        assertNotNull(lastTempDir);
        assertTrue(Files.exists(lastTempDir.resolve("process.json")));
        assertTrue(Files.exists(lastTempDir.resolve("machine.json")));
        assertTrue(Files.exists(lastTempDir.resolve("filament.json")));
    }

    @Test
    void testSlice_WithLayerHeightOverride_ShouldUpdateProcessJson() throws IOException {
        File dummyStl = tempDir.resolve("test.stl").toFile();
        Files.createFile(dummyStl.toPath());

        Map<String, String> processOverrides = new HashMap<>();
        processOverrides.put("layer_height", "0.12");

        slicerService.slice(dummyStl, "Bambu A1", "PLA", "Standard", null, processOverrides);

        File processJsonFile = lastTempDir.resolve("process.json").toFile();
        ObjectNode processJson = (ObjectNode) mapper.readTree(processJsonFile);
        
        assertTrue(processJson.has("layer_height"));
        assertEquals("0.12", processJson.get("layer_height").asText());
    }

    @Test
    void testSlice_WithInfillAndSupportOverrides_ShouldUpdateProcessJson() throws IOException {
        File dummyStl = tempDir.resolve("test.stl").toFile();
        Files.createFile(dummyStl.toPath());

        Map<String, String> processOverrides = new HashMap<>();
        processOverrides.put("sparse_infill_density", "25%");
        processOverrides.put("enable_support", "1");

        slicerService.slice(dummyStl, "Bambu A1", "PLA", "Standard", null, processOverrides);

        File processJsonFile = lastTempDir.resolve("process.json").toFile();
        ObjectNode processJson = (ObjectNode) mapper.readTree(processJsonFile);
        
        assertEquals("25%", processJson.get("sparse_infill_density").asText());
        assertEquals("1", processJson.get("enable_support").asText());
    }
}

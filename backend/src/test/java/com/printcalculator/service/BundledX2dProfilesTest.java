package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BundledX2dProfilesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void importedFilesMatchThePinnedReleaseManifest() throws Exception {
        Path root = Path.of("profiles/x2d");
        var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
        var release = mapper.readTree(Path.of("orcaslicer-release.json").toFile());
        assertEquals(release.path("sourceCommit"), manifest.path("commit"));
        var outputs = manifest.path("output_sha256").fields();
        int count = 0;
        while (outputs.hasNext()) {
            var entry = outputs.next();
            byte[] content = Files.readAllBytes(root.resolve(entry.getKey()));
            assertEquals(entry.getValue().asText(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                    entry.getKey());
            count++;
        }
        assertTrue(count >= 18);
    }

    @Test
    void x2dPresetsResolveIndependentlyOfTheOlderSharedPresets() throws Exception {
        var profiles = new ProfileManager("profiles", mapper);
        for (String nozzle : List.of("0.4", "0.6")) {
            String machine = "Bambu Lab X2D " + nozzle + " nozzle";
            var preset = profiles.getMergedProfile(machine, "machine");
            assertFalse(preset.has("inherits"));
            assertEquals(2, preset.path("nozzle_diameter").size());
            assertEquals(4, preset.path("printer_extruder_variant").size());
            assertTrue(profiles.findCompatibleProcessProfileName(machine, new BigDecimal("0.24"), "draft").isPresent());
            for (String filament : List.of("Generic PA-CF", "Bambu PET-CF", "Generic ASA")) {
                var profile = profiles.getMergedProfile(filament + " @BBL X2D"
                        + (nozzle.equals("0.4") ? " 0.4 nozzle" : ""), "filament");
                assertFalse(profile.has("inherits"));
                assertEquals(4, profile.path("filament_extruder_variant").size());
                assertTrue(profile.path("compatible_printers").toString().contains(machine));
            }
        }
    }
}

package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SlicerProfilePreparationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void selectsSupportedPlateForMeshButPreservesProjectPlate() throws Exception {
        var machine = mapper.createObjectNode();
        var filament = mapper.createObjectNode();
        filament.putArray("textured_plate_temp").add("70");
        var process = mapper.createObjectNode();
        SlicerProfilePreparation.prepare(machine, filament, process, false);
        assertFalse(process.has("curr_bed_type"));
        SlicerProfilePreparation.prepare(machine, filament, process, true);
        assertEquals("Textured PEI Plate", process.path("curr_bed_type").asText());
        process.put("curr_bed_type", "Engineering Plate");
        SlicerProfilePreparation.prepare(machine, filament, process, true);
        assertEquals("Engineering Plate", process.path("curr_bed_type").asText());
    }

    @Test
    void selectsDefaultP2sVariantWithoutChangingHardwareLimitsOrMaterialSpeeds() throws Exception {
        var machine = mapper.createObjectNode();
        machine.putArray("nozzle_diameter").add("0.4");
        machine.putArray("printer_extruder_id").add("1").add("1");
        machine.putArray("printer_extruder_variant").add("Direct Drive Standard").add("Direct Drive High Flow");
        machine.put("printable_height", "256");
        var filament = mapper.createObjectNode();
        filament.putArray("filament_extruder_variant").add("Direct Drive Standard").add("Direct Drive High Flow");
        filament.putArray("filament_max_volumetric_speed").add("12").add("24");
        SlicerProfilePreparation.prepare(machine, filament, mapper.createObjectNode(), true);
        assertEquals(1, machine.path("printer_extruder_variant").size());
        assertEquals("Direct Drive Standard", filament.path("filament_extruder_variant").path(0).asText());
        assertEquals(256, machine.path("printable_height").asInt());
        assertEquals(2, filament.path("filament_max_volumetric_speed").size());
        assertEquals("0.4", machine.path("nozzle_diameter").path(0).asText());
    }

    @Test
    void preservesActualMultipleExtruders() throws Exception {
        var machine = mapper.createObjectNode();
        machine.putArray("nozzle_diameter").add("0.4").add("0.4");
        machine.putArray("printer_extruder_variant").add("Direct Drive Standard").add("Direct Drive High Flow");
        var before = machine.deepCopy();
        SlicerProfilePreparation.prepare(machine, mapper.createObjectNode(), mapper.createObjectNode(), true);
        assertEquals(before, machine);
    }

    @Test
    void mapsX2dFilamentsToDifferentNozzlesAndUsesTheirSharedVolume() throws Exception {
        var profiles = new ProfileManager("profiles", mapper);
        var machine = profiles.getMergedProfile("Bambu Lab X2D 0.4 nozzle", "machine");
        var model = profiles.getMergedProfile("Generic PA-CF @BBL X2D 0.4 nozzle", "filament");
        var asa = profiles.getMergedProfile("Generic ASA @BBL X2D 0.4 nozzle", "filament");
        var originalAsa = asa.deepCopy();
        var variants = machine.path("printer_extruder_variant").deepCopy();
        var process = mapper.createObjectNode();
        SlicerProfilePreparation.prepareAsaSupports(machine, model, asa, process, "#000000");
        SlicerProfilePreparation.prepare(machine, model, process, true);
        assertEquals("1", process.path("filament_map").path(0).asText());
        assertEquals("2", process.path("filament_map").path(1).asText());
        assertEquals("Manual", process.path("filament_map_mode").asText());
        assertEquals("2", process.path("support_interface_filament").asText());
        assertEquals(variants, machine.path("printer_extruder_variant"));
        assertEquals(originalAsa, asa);
        var volume = SlicerService.extractPrintableVolume(machine).orElseThrow();
        assertEquals(235.5, volume.xMm());
        assertEquals(256, volume.yMm());
        assertEquals(256, volume.zMm());
        assertEquals(35.5, process.path("wipe_tower_x").path(0).asDouble());
    }

    @ParameterizedTest
    @CsvSource({"0.4,1000", "0.6,4000"})
    void usesAuxiliaryAccelerationForSupportsAndPreservesModelRoleAccelerations(String nozzle, double supportAcceleration) throws Exception {
        var profiles = new ProfileManager("profiles", mapper);
        String machineName = "Bambu Lab X2D " + nozzle + " nozzle";
        var machine = profiles.getMergedProfile(machineName, "machine");
        String processName = profiles.findCompatibleProcessProfileName(machineName, new BigDecimal("0.24"), "draft")
                .orElseThrow();
        var process = profiles.getMergedProfile(processName, "process");
        var asa = mapper.createObjectNode();
        asa.putArray("filament_type").add("ASA");
        SlicerProfilePreparation.prepareAsaSupports(machine, mapper.createObjectNode(), asa, process, "#000000");
        assertEquals(supportAcceleration, process.path("default_acceleration").asDouble());
        assertEquals(10000, process.path("inner_wall_acceleration").asDouble());
        assertEquals(10000, process.path("sparse_infill_acceleration").asDouble());
        assertEquals(10000, process.path("internal_solid_infill_acceleration").asDouble());
        assertEquals(2500, process.path("bridge_acceleration").asDouble());
        assertEquals(5000, process.path("outer_wall_acceleration").asDouble());
        assertEquals(2000, process.path("top_surface_acceleration").asDouble());
        assertEquals(10000, process.path("travel_acceleration").path(0).asDouble());
    }

    @Test
    void resolvesTheSelectedFlowVariantAndPercentagesWithoutGuessingItsAcceleration() throws Exception {
        var profiles = new ProfileManager("profiles", mapper);
        var machine = profiles.getMergedProfile("Bambu Lab X2D 0.4 nozzle", "machine");
        machine.putArray("default_nozzle_volume_type").add("High Flow").add("Standard");
        var process = profiles.getMergedProfile("0.24mm Standard @BBL X2D", "process");
        process.putArray("default_acceleration").add("9000").add("6000").add("700").add("800");
        process.putArray("sparse_infill_acceleration").add("100%").add("75%").add("100%").add("100%");
        var asa = mapper.createObjectNode();
        asa.putArray("filament_type").add("ASA");
        SlicerProfilePreparation.prepareAsaSupports(machine, mapper.createObjectNode(), asa, process, "#000000");
        assertEquals(700, process.path("default_acceleration").asDouble());
        assertEquals(6000, process.path("inner_wall_acceleration").asDouble());
        assertEquals(4500, process.path("sparse_infill_acceleration").asDouble());
        assertEquals(2500, process.path("bridge_acceleration").asDouble());
    }

    @Test
    void keepsP2sAccelerationConfigurationUnchanged() throws Exception {
        var machine = mapper.createObjectNode();
        machine.put("printer_model", "Bambu Lab P2S");
        var process = mapper.createObjectNode();
        process.putArray("default_acceleration").add("10000").add("8000");
        var original = process.path("default_acceleration").deepCopy();
        var asa = mapper.createObjectNode();
        asa.putArray("filament_type").add("ASA");
        SlicerProfilePreparation.prepareAsaSupports(machine, mapper.createObjectNode(), asa, process, "#000000");
        assertEquals(original, process.path("default_acceleration"));
    }
}

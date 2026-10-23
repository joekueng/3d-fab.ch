package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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
}

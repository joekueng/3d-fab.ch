package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** Prepare temporary CLI profiles without changing the stored printer presets. */
final class SlicerProfilePreparation {
    private SlicerProfilePreparation() {}

    static void prepare(ObjectNode machine, ObjectNode filament, ObjectNode process, boolean meshInput) throws IOException {
        // A raw mesh has no project plate selection. Orca otherwise defaults to
        // Cool Plate, which rejects PETG even with a valid printer/material pair.
        if (meshInput && !process.hasNonNull("curr_bed_type")
                && filament.path("textured_plate_temp").path(0).asInt(0) > 0) {
            process.put("curr_bed_type", "Textured PEI Plate");
        }

        JsonNode variants = machine.path("printer_extruder_variant");
        JsonNode ids = machine.path("printer_extruder_id");
        if (machine.path("nozzle_diameter").size() != 1 || !variants.isArray() || variants.size() < 2
                || !ids.isArray() || ids.isEmpty()) return;
        for (JsonNode id : ids) {
            if (id.asInt() != 1) return; // Preserve actual printers with multiple extruders.
        }
        String defaultVariant = variants.path(0).asText();
        JsonNode filamentVariants = filament.path("filament_extruder_variant");
        if (filamentVariants.isArray() && filamentVariants.size() > 0
                && !defaultVariant.equals(filamentVariants.path(0).asText())) {
            throw new IOException("The printer and filament default extruder variants do not match");
        }
        // P2S declares Standard and High Flow alternatives for one physical
        // extruder. Raw-mesh CLI jobs in Orca 2.3.x crash while expanding those
        // alternatives. Select the preset's first/default variant explicitly;
        // retain all print speeds, temperatures, nozzle size and machine limits.
        machine.putArray("printer_extruder_variant").add(defaultVariant);
        machine.putArray("printer_extruder_id").add("1");
        machine.putArray("extruder_variant_list").add(defaultVariant);
        if (filamentVariants.isArray() && filamentVariants.size() > 0) {
            filament.putArray("filament_extruder_variant").add(defaultVariant);
        }
    }
}

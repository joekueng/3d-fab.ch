package com.printcalculator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;

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

    static void prepareAsaSupports(ObjectNode machineProfile, ObjectNode filamentProfile, ObjectNode supportFilamentProfile,
                                   ObjectNode processProfile, String modelFilamentColor) throws IOException {
        if (!"ASA".equalsIgnoreCase(supportFilamentProfile.path("filament_type").path(0).asText())) {
            throw new IOException("The engineering-material support profile must be ASA");
        }
        boolean dualNozzle = "Bambu Lab X2D".equals(machineProfile.path("printer_model").asText());
        if (dualNozzle && machineProfile.path("nozzle_diameter").size() != 2) {
            throw new IOException("The X2D profile must contain both physical nozzles");
        }
        if (dualNozzle) {
            prepareX2dAccelerations(machineProfile, processProfile);
            double minX = restrictToDualNozzleVolume(machineProfile);
            // Orca's raw-mesh tower anchor defaults to x=15 in absolute bed coordinates.
            // The auxiliary nozzle starts at x=20.5, so offset the anchor into the shared area.
            processProfile.putArray("wipe_tower_x").add(Double.toString(minX + 15));
        }
        processProfile.putArray("filament_map").add("1").add(dualNozzle ? "2" : "1");
        processProfile.put("filament_map_mode", "Manual");
        processProfile.put("support_filament", "2");
        processProfile.put("support_interface_filament", "2");
        processProfile.put("support_on_build_plate_only", "0");
        processProfile.put("wall_filament", "1");
        processProfile.put("sparse_infill_filament", "1");
        processProfile.put("solid_infill_filament", "1");
        processProfile.put("enable_prime_tower", "1");
        processProfile.put("flush_into_infill", "0");
        processProfile.put("flush_into_objects", "0");
        processProfile.put("flush_into_support", "0");
        // Raw meshes have no project palette. Orca needs one entry per loaded filament
        // to initialize its purge matrix; otherwise 2.3.2 can crash with a prime tower.
        processProfile.putArray("filament_colour")
                .add(resolveFilamentColor(filamentProfile, modelFilamentColor))
                .add(resolveFilamentColor(supportFilamentProfile, null));
    }

    private static void prepareX2dAccelerations(ObjectNode machine, ObjectNode process) throws IOException {
        JsonNode acceleration = process.path("default_acceleration");
        if (!acceleration.isArray()) return;
        int modelVariant = selectedProcessVariant(machine, process, 1);
        int supportVariant = selectedProcessVariant(machine, process, 2);
        double modelAcceleration = positiveAcceleration(acceleration.path(modelVariant).asText(), "model");
        double supportAcceleration = positiveAcceleration(acceleration.path(supportVariant).asText(), "support");

        // Orca 2.4.2 reads process acceleration arrays as a scalar (first entry).
        // Supports use the global normal acceleration; restore explicit model-role
        // values before lowering it to the auxiliary nozzle's actual preset value.
        for (String key : List.of("inner_wall_acceleration", "outer_wall_acceleration", "top_surface_acceleration",
                "sparse_infill_acceleration", "internal_solid_infill_acceleration", "bridge_acceleration")) {
            JsonNode configured = process.path(key);
            // Native defaults: bridge 50% of outer-wall acceleration, infill 100%
            // of normal acceleration; zero-valued roles inherit normal acceleration.
            String fallback = "bridge_acceleration".equals(key) ? "50%" : "100%";
            String value = configured.isArray() ? configured.path(modelVariant).asText(fallback)
                    : configured.asText(fallback);
            try {
                boolean percentage = value.endsWith("%");
                double number = Double.parseDouble(percentage ? value.substring(0, value.length() - 1) : value);
                double reference = "bridge_acceleration".equals(key)
                        ? process.path("outer_wall_acceleration").asDouble(modelAcceleration) : modelAcceleration;
                double resolved = number == 0 ? modelAcceleration
                        : percentage ? number * reference / 100 : number;
                if (!Double.isFinite(resolved) || resolved <= 0) throw new NumberFormatException();
                process.put(key, Double.toString(resolved));
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid X2D acceleration: " + key, ex);
            }
        }
        process.put("default_acceleration", Double.toString(Math.min(modelAcceleration, supportAcceleration)));
    }

    private static int selectedProcessVariant(ObjectNode machine, ObjectNode process, int nozzle) throws IOException {
        String volume = machine.path("default_nozzle_volume_type").path(nozzle - 1).asText("Standard");
        JsonNode ids = process.path("print_extruder_id");
        JsonNode variants = process.path("print_extruder_variant");
        for (int i = 0; i < ids.size(); i++) {
            if (ids.path(i).asInt() == nozzle && variants.path(i).asText().endsWith(" " + volume)) return i;
        }
        throw new IOException("Missing X2D process acceleration variant for nozzle " + nozzle);
    }

    private static double positiveAcceleration(String value, String role) throws IOException {
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number) || number <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid X2D " + role + " normal acceleration", ex);
        }
    }

    private static String resolveFilamentColor(ObjectNode filament, String selectedColor) {
        for (String color : List.of(selectedColor == null ? "" : selectedColor,
                filament.path("filament_colour").path(0).asText(""),
                filament.path("default_filament_colour").path(0).asText(""))) {
            if (color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return color;
        }
        return "#FFFFFF";
    }

    private static double restrictToDualNozzleVolume(ObjectNode machine) throws IOException {
        JsonNode areas = machine.path("extruder_printable_area");
        JsonNode heights = machine.path("extruder_printable_height");
        if (areas.size() != 2 || heights.size() != 2) {
            throw new IOException("The X2D profile must define both nozzle print volumes");
        }
        double minX = Double.NEGATIVE_INFINITY, minY = Double.NEGATIVE_INFINITY;
        double maxX = Double.POSITIVE_INFINITY, maxY = Double.POSITIVE_INFINITY;
        double height = Double.POSITIVE_INFINITY;
        try {
            for (int nozzle = 0; nozzle < 2; nozzle++) {
                double left = Double.POSITIVE_INFINITY, bottom = Double.POSITIVE_INFINITY;
                double right = Double.NEGATIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
                for (String point : areas.path(nozzle).asText().split(",")) {
                    String[] xy = point.trim().split("x");
                    double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    bottom = Math.min(bottom, y);
                    top = Math.max(top, y);
                }
                minX = Math.max(minX, left);
                maxX = Math.min(maxX, right);
                minY = Math.max(minY, bottom);
                maxY = Math.min(maxY, top);
                height = Math.min(height, Double.parseDouble(heights.path(nozzle).asText()));
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            throw new IOException("Invalid X2D nozzle print volume", ex);
        }
        if (!(maxX > minX && maxY > minY && height > 0)
                || !Double.isFinite(maxX - minX + maxY - minY + height)) {
            throw new IOException("The X2D nozzles have no shared printable volume");
        }
        machine.putArray("printable_area").add(minX + "x" + minY).add(maxX + "x" + minY)
                .add(maxX + "x" + maxY).add(minX + "x" + maxY);
        machine.put("printable_height", Double.toString(height));
        return minX;
    }
}

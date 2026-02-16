package com.printcalculator.controller;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.exception.ModelTooLargeException;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.model.StlBounds;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.StlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@RestController
public class QuoteController {

    private static final Logger logger = Logger.getLogger(QuoteController.class.getName());

    private final SlicerService slicerService;
    private final StlService stlService;
    private final QuoteCalculator quoteCalculator;
    private final PrinterMachineRepository machineRepo;
    private final ProfileManager profileManager;

    // Defaults (using aliases defined in ProfileManager)
    private static final String DEFAULT_FILAMENT = "pla_basic";
    private static final String DEFAULT_PROCESS = "standard";

    public QuoteController(SlicerService slicerService, StlService stlService, QuoteCalculator quoteCalculator, PrinterMachineRepository machineRepo, ProfileManager profileManager) {
        this.slicerService = slicerService;
        this.stlService = stlService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
        this.profileManager = profileManager;
    }

    @PostMapping("/api/quote")
    public ResponseEntity<QuoteResult> calculateQuote(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filament", required = false, defaultValue = DEFAULT_FILAMENT) String filament,
            @RequestParam(value = "process", required = false) String process,
            @RequestParam(value = "quality", required = false) String quality,
            // Advanced Options
            @RequestParam(value = "infill_density", required = false) Integer infillDensity,
            @RequestParam(value = "infill_pattern", required = false) String infillPattern,
            @RequestParam(value = "layer_height", required = false) Double layerHeight,
            @RequestParam(value = "nozzle_diameter", required = false) Double nozzleDiameter,
            @RequestParam(value = "support_enabled", required = false, defaultValue = "false") Boolean supportEnabled
            ) throws IOException {

        // ... process selection logic ...
        String actualProcess = process;
        if (actualProcess == null || actualProcess.isEmpty()) {
            if (quality != null && !quality.isEmpty()) {
                actualProcess = quality;
            } else {
                actualProcess = DEFAULT_PROCESS;
            }
        }

        // Prepare Overrides
        Map<String, String> processOverrides = new HashMap<>();
        Map<String, String> machineOverrides = new HashMap<>();

        if (infillDensity != null) {
            processOverrides.put("sparse_infill_density", infillDensity + "%");
        }
        if (infillPattern != null && !infillPattern.isEmpty()) {
            processOverrides.put("sparse_infill_pattern", infillPattern);
        }
        if (layerHeight != null) {
            processOverrides.put("layer_height", String.valueOf(layerHeight));
        }
        if (supportEnabled != null) {
            processOverrides.put("enable_support", supportEnabled ? "1" : "0");
            if (supportEnabled) {
                processOverrides.put("support_threshold_angle", "45");
            }
        }

        if (nozzleDiameter != null) {
            machineOverrides.put("nozzle_diameter", String.valueOf(nozzleDiameter));
            // Also need to ensure the printer profile is compatible or just override?
            // Usually nozzle diameter changes require a different printer profile or deep overrides.
            // For now, we trust the override key works on the base profile.
        }

        return processRequest(file, filament, actualProcess, machineOverrides, processOverrides);
    }

    @PostMapping("/calculate/stl")
    public ResponseEntity<QuoteResult> legacyCalculate(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // Legacy endpoint uses defaults
        return processRequest(file, DEFAULT_FILAMENT, DEFAULT_PROCESS, null, null);
    }

    private ResponseEntity<QuoteResult> processRequest(MultipartFile file, String filament, String process,
                                                       Map<String, String> machineOverrides,
                                                       Map<String, String> processOverrides) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Fetch Default Active Machine
        PrinterMachine machine = machineRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IOException("No active printer found in database"));

        // Save uploaded file temporarily
        Path tempInput = Files.createTempFile("upload_", "_" + file.getOriginalFilename());
        com.printcalculator.model.StlShiftResult shift = null;
        try {
            file.transferTo(tempInput.toFile());

            // Use profile from machine or fallback
            String slicerMachineProfile = machine.getSlicerMachineProfile();
            if (slicerMachineProfile == null || slicerMachineProfile.isEmpty()) {
                slicerMachineProfile = "bambu_a1"; 
            }
            slicerMachineProfile = profileManager.resolveMachineProfileName(slicerMachineProfile, nozzleDiameter);

            // Validate model size against machine volume
            StlBounds bounds = validateModelSize(tempInput.toFile(), machine);

            // Auto-center if needed
            shift = stlService.shiftToFitIfNeeded(
                    tempInput.toFile(),
                    bounds,
                    machine.getBuildVolumeXMm(),
                    machine.getBuildVolumeYMm(),
                    machine.getBuildVolumeZMm()
            );
            java.io.File sliceInput = shift.shifted() ? shift.shiftedPath().toFile() : tempInput.toFile();
            if (shift.shifted()) {
                logger.info(String.format("Auto-centered STL by offset (mm): x=%.3f y=%.3f z=%.3f",
                        shift.offsetX(), shift.offsetY(), shift.offsetZ()));
            }

            PrintStats stats = slicerService.slice(sliceInput, slicerMachineProfile, filament, process, machineOverrides, processOverrides);
            
            // Calculate Quote (Pass machine display name for pricing lookup)
            QuoteResult result = quoteCalculator.calculate(stats, machine.getPrinterDisplayName(), filament);
            
            return ResponseEntity.ok(result);
        } finally {
            Files.deleteIfExists(tempInput);
            if (shift != null && shift.shifted()) {
                try {
                    Files.deleteIfExists(shift.shiftedPath());
                } catch (Exception ignored) {}
            }
        }
    }

    private StlBounds validateModelSize(java.io.File stlFile, PrinterMachine machine) throws IOException {
        StlBounds bounds = stlService.readBounds(stlFile);
        double x = bounds.sizeX();
        double y = bounds.sizeY();
        double z = bounds.sizeZ();

        int bx = machine.getBuildVolumeXMm();
        int by = machine.getBuildVolumeYMm();
        int bz = machine.getBuildVolumeZMm();

        logger.info(String.format(
                "STL bounds (mm): min(%.3f,%.3f,%.3f) max(%.3f,%.3f,%.3f) size(%.3f,%.3f,%.3f) bed(%d,%d,%d)",
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                x, y, z, bx, by, bz
        ));

        double eps = 0.01;
        boolean fits = (x <= bx + eps && y <= by + eps && z <= bz + eps)
                || (y <= bx + eps && x <= by + eps && z <= bz + eps);

        if (!fits) {
            throw new ModelTooLargeException(x, y, z, bx, by, bz);
        }
        return bounds;
    }
}

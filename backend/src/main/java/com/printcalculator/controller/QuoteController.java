package com.printcalculator.controller;

import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.SlicerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@CrossOrigin(origins = "*") // Allow all for development
public class QuoteController {

    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;

    // Defaults
    private static final String DEFAULT_MACHINE = "Bambu_Lab_A1_machine";
    private static final String DEFAULT_FILAMENT = "Bambu_PLA_Basic";
    private static final String DEFAULT_PROCESS = "Bambu_Process_0.20_Standard";

    public QuoteController(SlicerService slicerService, QuoteCalculator quoteCalculator) {
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
    }

    @PostMapping("/api/quote")
    public ResponseEntity<QuoteResult> calculateQuote(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "machine", defaultValue = DEFAULT_MACHINE) String machine,
            @RequestParam(value = "filament", defaultValue = DEFAULT_FILAMENT) String filament,
            @RequestParam(value = "process", defaultValue = DEFAULT_PROCESS) String process
            ) throws IOException {

        return processRequest(file, machine, filament, process);
    }

    @PostMapping("/calculate/stl")
    public ResponseEntity<QuoteResult> legacyCalculate(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // Legacy endpoint uses defaults
        return processRequest(file, DEFAULT_MACHINE, DEFAULT_FILAMENT, DEFAULT_PROCESS);
    }

    private ResponseEntity<QuoteResult> processRequest(MultipartFile file, String machine, String filament, String process) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Save uploaded file temporarily
        Path tempInput = Files.createTempFile("upload_", "_" + file.getOriginalFilename());
        try {
            file.transferTo(tempInput.toFile());

            // Slice
            PrintStats stats = slicerService.slice(tempInput.toFile(), machine, filament, process);
            
            // Calculate Quote
            QuoteResult result = quoteCalculator.calculate(stats);
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build(); // Simplify error handling for now
        } finally {
            Files.deleteIfExists(tempInput);
        }
    }
}

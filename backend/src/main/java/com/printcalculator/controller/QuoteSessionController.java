package com.printcalculator.controller;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.model.PrintStats;
import com.printcalculator.model.QuoteResult;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.SlicerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@RestController
@RequestMapping("/api/quote-sessions")

public class QuoteSessionController {

    private final QuoteSessionRepository sessionRepo;
    private final QuoteLineItemRepository lineItemRepo;
    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;
    private final PrinterMachineRepository machineRepo;
    private final com.printcalculator.repository.PricingPolicyRepository pricingRepo;
    private final com.printcalculator.service.StorageService storageService;

    // Defaults
    private static final String DEFAULT_FILAMENT = "pla_basic";
    private static final String DEFAULT_PROCESS = "standard";

    public QuoteSessionController(QuoteSessionRepository sessionRepo,
                                  QuoteLineItemRepository lineItemRepo,
                                  SlicerService slicerService,
                                  QuoteCalculator quoteCalculator,
                                  PrinterMachineRepository machineRepo,
                                  com.printcalculator.repository.PricingPolicyRepository pricingRepo,
                                  com.printcalculator.service.StorageService storageService) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
        this.pricingRepo = pricingRepo;
        this.storageService = storageService;
    }

    // 1. Start a new empty session
    @PostMapping(value = "")
    @Transactional
    public ResponseEntity<QuoteSession> createSession() {
        QuoteSession session = new QuoteSession();
        session.setStatus("ACTIVE");
        session.setPricingVersion("v1");
        // Default material/settings will be set when items are added or updated?
        // For now set safe defaults
        session.setMaterialCode("pla_basic"); 
        session.setSupportsEnabled(true);
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plusDays(30)); 
        
        var policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        session.setSetupCostChf(policy != null ? policy.getFixedJobFeeChf() : BigDecimal.ZERO);
        
        session = sessionRepo.save(session);
        return ResponseEntity.ok(session);
    }
    
    // 2. Add item to existing session
    @PostMapping(value = "/{id}/line-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<QuoteLineItem> addItemToExistingSession(
            @PathVariable UUID id,
            @RequestPart("settings") com.printcalculator.dto.PrintSettingsDto settings,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        QuoteLineItem item = addItemToSession(session, file, settings);
        return ResponseEntity.ok(item);
    }

    // Helper to add item
    private QuoteLineItem addItemToSession(QuoteSession session, MultipartFile file, com.printcalculator.dto.PrintSettingsDto settings) throws IOException {
        if (file.isEmpty()) throw new IOException("File is empty");

        // 1. Define Persistent Storage Path
        // Structure: quotes/{sessionId}/{uuid}.{ext} (inside storage root)
        
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".") 
                     ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                     : ".stl";
        
        String storedFilename = UUID.randomUUID() + ext;
        Path relativePath = Paths.get("quotes", session.getId().toString(), storedFilename);
        
        // Save file
        storageService.store(file, relativePath);
        
        // Resolve absolute path for slicing and storage usage
        Path persistentPath = storageService.loadAsResource(relativePath).getFile().toPath();

        try {
            // Apply Basic/Advanced Logic
            applyPrintSettings(settings);

            // REAL SLICING
            // 1. Pick Machine (default to first active or specific)
            PrinterMachine machine = machineRepo.findFirstByIsActiveTrue()
                    .orElseThrow(() -> new RuntimeException("No active printer found"));
            
            // 2. Pick Profiles
            String machineProfile = machine.getPrinterDisplayName(); // e.g. "Bambu Lab A1 0.4 nozzle"
            // If the display name doesn't match the json profile name, we might need a mapping key in DB.
            // For now assuming display name works or we use a tough default
             machineProfile = "Bambu Lab A1 0.4 nozzle"; // Force known good for now? Or use DB field if exists. 
             // Ideally: machine.getSlicerProfileName();
            
            String filamentProfile = "Generic " + (settings.getMaterial() != null ? settings.getMaterial().toUpperCase() : "PLA");
            // Mapping: "pla_basic" -> "Generic PLA", "petg_basic" -> "Generic PETG"
            if (settings.getMaterial() != null) {
                if (settings.getMaterial().toLowerCase().contains("pla")) filamentProfile = "Generic PLA";
                else if (settings.getMaterial().toLowerCase().contains("petg")) filamentProfile = "Generic PETG";
                else if (settings.getMaterial().toLowerCase().contains("tpu")) filamentProfile = "Generic TPU";
                else if (settings.getMaterial().toLowerCase().contains("abs")) filamentProfile = "Generic ABS";
                
                // Update Session Material
                session.setMaterialCode(settings.getMaterial());
            } else {
                 // Fallback if null?
                 session.setMaterialCode("pla_basic");
            }
            
            // Update Session Settings for Persistence
            if (settings.getNozzleDiameter() != null) session.setNozzleDiameterMm(BigDecimal.valueOf(settings.getNozzleDiameter()));
            if (settings.getLayerHeight() != null) session.setLayerHeightMm(BigDecimal.valueOf(settings.getLayerHeight()));
            if (settings.getInfillDensity() != null) session.setInfillPercent(settings.getInfillDensity().intValue());
            if (settings.getInfillPattern() != null) session.setInfillPattern(settings.getInfillPattern());
            if (settings.getSupportsEnabled() != null) session.setSupportsEnabled(settings.getSupportsEnabled());
            if (settings.getNotes() != null) session.setNotes(settings.getNotes());
            
            // Save session updates
            sessionRepo.save(session);

            String processProfile = "0.20mm Standard @BBL A1"; 
            // Mapping quality to process
            // "standard" -> "0.20mm Standard @BBL A1"
            // "draft" -> "0.28mm Extra Draft @BBL A1"
            // "high" -> "0.12mm Fine @BBL A1" (approx names, need to be exact for Orca)
            // Let's use robust defaults or simple overrides
            if (settings.getLayerHeight() != null) {
                 if (settings.getLayerHeight() >= 0.28) processProfile = "0.28mm Extra Draft @BBL A1";
                 else if (settings.getLayerHeight() <= 0.12) processProfile = "0.12mm Fine @BBL A1";
            }
            
            // Build overrides map from settings
            // Build overrides map from settings
            Map<String, String> processOverrides = new HashMap<>();
            if (settings.getLayerHeight() != null) processOverrides.put("layer_height", String.valueOf(settings.getLayerHeight()));
            if (settings.getInfillDensity() != null) processOverrides.put("sparse_infill_density", settings.getInfillDensity() + "%");
            if (settings.getInfillPattern() != null) processOverrides.put("sparse_infill_pattern", settings.getInfillPattern());
            if (settings.getSupportsEnabled() != null) {
                processOverrides.put("enable_support", settings.getSupportsEnabled() ? "1" : "0");
                // If enabled, use a more permissive threshold (45 deg) by default
                // to avoid expensive supports on things that don't strictly need them
                if (settings.getSupportsEnabled()) {
                    processOverrides.put("support_threshold_angle", "45");
                }
            }
            
            Map<String, String> machineOverrides = new HashMap<>();
            if (settings.getNozzleDiameter() != null) {
                machineOverrides.put("nozzle_diameter", String.valueOf(settings.getNozzleDiameter()));
            }

            // 3. Slice (Use persistent path)
            PrintStats stats = slicerService.slice(
                persistentPath.toFile(), 
                machineProfile, 
                filamentProfile, 
                processProfile, 
                machineOverrides, // machine overrides
                processOverrides
            );
            
            // 4. Calculate Quote
            QuoteResult result = quoteCalculator.calculate(stats, machine.getPrinterDisplayName(), filamentProfile);

            // 5. Create Line Item
            QuoteLineItem item = new QuoteLineItem();
            item.setQuoteSession(session);
            item.setOriginalFilename(file.getOriginalFilename());
            item.setStoredPath(persistentPath.toString()); // SAVE PATH
            item.setQuantity(1);
            item.setColorCode(settings.getColor() != null ? settings.getColor() : "#FFFFFF");
            item.setStatus("READY"); // or CALCULATED
            
            item.setPrintTimeSeconds((int) stats.getPrintTimeSeconds());
            item.setMaterialGrams(BigDecimal.valueOf(stats.getFilamentWeightGrams()));
            item.setUnitPriceChf(BigDecimal.valueOf(result.getTotalPrice()));
            
            // Store breakdown
            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("machine_cost", result.getTotalPrice() - result.getSetupCost()); // Approximation? 
            // Better: QuoteResult could expose detailed breakdown. For now just storing what we have.
            breakdown.put("setup_fee", result.getSetupCost());
            item.setPricingBreakdown(breakdown);
            
            // Dimensions
            // Cannot get bb from GCodeParser yet? 
            // If GCodeParser doesn't return size, we might defaults or 0.
            // Stats has filament used. 
            // Let's set dummy for now or upgrade parser later.
            item.setBoundingBoxXMm(BigDecimal.ZERO);
            item.setBoundingBoxYMm(BigDecimal.ZERO);
            item.setBoundingBoxZMm(BigDecimal.ZERO);
            
            item.setCreatedAt(OffsetDateTime.now());
            item.setUpdatedAt(OffsetDateTime.now());
            
            return lineItemRepo.save(item);

        } catch (Exception e) {
            // Cleanup if failed
            try {
                storageService.delete(Paths.get("quotes", session.getId().toString(), storedFilename));
            } catch (Exception ignored) {}
            throw e;
        }
    }

    private void applyPrintSettings(com.printcalculator.dto.PrintSettingsDto settings) {
        if ("BASIC".equalsIgnoreCase(settings.getComplexityMode())) {
            // Set defaults based on Quality
            String quality = settings.getQuality() != null ? settings.getQuality().toLowerCase() : "standard";
            
            switch (quality) {
                case "draft":
                    settings.setLayerHeight(0.28);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                    if (settings.getNozzleDiameter() == null) settings.setNozzleDiameter(0.4);
                    break;
                case "high":
                    settings.setLayerHeight(0.12);
                    settings.setInfillDensity(20.0);
                    settings.setInfillPattern("gyroid");
                    if (settings.getNozzleDiameter() == null) settings.setNozzleDiameter(0.4);
                    break;
                case "standard":
                default:
                    settings.setLayerHeight(0.20);
                    settings.setInfillDensity(20.0);
                    settings.setInfillPattern("grid");
                    if (settings.getNozzleDiameter() == null) settings.setNozzleDiameter(0.4);
                    break;
            }
            if (settings.getSupportsEnabled() == null) settings.setSupportsEnabled(true);
        } else {
            // ADVANCED Mode: Use values from Frontend, set defaults if missing
            if (settings.getLayerHeight() == null) settings.setLayerHeight(0.20);
            if (settings.getInfillDensity() == null) settings.setInfillDensity(20.0);
            if (settings.getInfillPattern() == null) settings.setInfillPattern("grid");
            if (settings.getNozzleDiameter() == null) settings.setNozzleDiameter(0.4);
            if (settings.getSupportsEnabled() == null) settings.setSupportsEnabled(true);
        }
    }

    // 3. Update Line Item
    @PatchMapping("/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<QuoteLineItem> updateLineItem(
            @PathVariable UUID lineItemId,
            @RequestBody Map<String, Object> updates
    ) {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        if (updates.containsKey("quantity")) {
            item.setQuantity((Integer) updates.get("quantity"));
        }
        if (updates.containsKey("color_code")) {
            item.setColorCode((String) updates.get("color_code"));
        }
        
        // Recalculate price if needed? 
        // For now, unit price is fixed in mock. Total is calculated on GET.
        
        item.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(lineItemRepo.save(item));
    }
    
    // 4. Delete Line Item
    @DeleteMapping("/{sessionId}/line-items/{lineItemId}")
    @Transactional
    public ResponseEntity<Void> deleteLineItem(
            @PathVariable UUID sessionId,
            @PathVariable UUID lineItemId
    ) {
        // Verify item belongs to session?
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }
        
        lineItemRepo.delete(item);
        return ResponseEntity.noContent().build();
    }
    
    // 5. Get Session (Session + Items + Total)
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getQuoteSession(@PathVariable UUID id) {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        List<QuoteLineItem> items = lineItemRepo.findByQuoteSessionId(id);
        
        // Calculate Totals
        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (QuoteLineItem item : items) {
            BigDecimal lineTotal = item.getUnitPriceChf().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(lineTotal);
        }
        
        BigDecimal setupFee = session.getSetupCostChf() != null ? session.getSetupCostChf() : BigDecimal.ZERO;
        BigDecimal grandTotal = itemsTotal.add(setupFee);
        
        Map<String, Object> response = new HashMap<>();
        response.put("session", session);
        response.put("items", items);
        response.put("itemsTotalChf", itemsTotal);
        response.put("grandTotalChf", grandTotal);
        
        return ResponseEntity.ok(response);
    }

    // 6. Download Line Item Content
    @GetMapping(value = "/{sessionId}/line-items/{lineItemId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> downloadLineItemContent(
            @PathVariable UUID sessionId,
            @PathVariable UUID lineItemId
    ) throws IOException {
        QuoteLineItem item = lineItemRepo.findById(lineItemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getQuoteSession().getId().equals(sessionId)) {
            return ResponseEntity.badRequest().build();
        }

        if (item.getStoredPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path path = Paths.get(item.getStoredPath());
        // Since storedPath is absolute, we can't directly use loadAsResource with it unless we resolve relative.
        // But loadAsResource expects relative path?
        // Actually FileSystemStorageService.loadAsResource uses rootLocation.resolve(path).
        // If path is absolute, resolve might fail or behave weirdly.
        // But wait, we stored absolute path in DB: item.setStoredPath(persistentPath.toString());
        // If we want to use storageService.loadAsResource, we need the relative path.
        // Or we just access the file directly if we trust the absolute path.
        // But we want to use StorageService abstraction.
        
        // Option 1: Reconstruct relative path.
        // We know structure: quotes/{sessionId}/{filename}... 
        // But filename is UUID+ext. We don't have storedFilename in QuoteLineItem easily?
        // QuoteLineItem doesn't seem to have storedFilename field, only storedPath.
        
        // If we trust the file is on disk, we can use UrlResource directly here as before,
        // relying on the fact that storedPath is the absolute path to the file.
        // But we should verify it exists.
        
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + item.getOriginalFilename() + "\"")
                .body(resource);
    }
}

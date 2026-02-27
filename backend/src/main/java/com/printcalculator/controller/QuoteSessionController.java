package com.printcalculator.controller;

import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.model.ModelDimensions;
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
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
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
    private final com.printcalculator.service.ClamAVService clamAVService;

    // Defaults
    private static final String DEFAULT_FILAMENT = "pla_basic";
    private static final String DEFAULT_PROCESS = "standard";

    public QuoteSessionController(QuoteSessionRepository sessionRepo,
                                  QuoteLineItemRepository lineItemRepo,
                                  SlicerService slicerService,
                                  QuoteCalculator quoteCalculator,
                                  PrinterMachineRepository machineRepo,
                                  com.printcalculator.repository.PricingPolicyRepository pricingRepo,
                                  com.printcalculator.service.ClamAVService clamAVService) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
        this.pricingRepo = pricingRepo;
        this.clamAVService = clamAVService;
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
        session.setMaterialCode("PLA"); 
        session.setSupportsEnabled(false);
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

        // Scan for virus
        clamAVService.scan(file.getInputStream());

        // 1. Define Persistent Storage Path
        // Structure: storage_quotes/{sessionId}/{uuid}.{ext}
        String storageDir = "storage_quotes/" + session.getId();
        Files.createDirectories(Paths.get(storageDir));
        
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".") 
                     ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                     : ".stl";
        
        String storedFilename = UUID.randomUUID() + ext;
        Path persistentPath = Paths.get(storageDir, storedFilename);
        
        // Save file
        Files.copy(file.getInputStream(), persistentPath);

        try {
            // Apply Basic/Advanced Logic
            applyPrintSettings(settings);

            // Update session global settings from the most recent item added
            session.setMaterialCode(settings.getMaterial());
            session.setNozzleDiameterMm(BigDecimal.valueOf(settings.getNozzleDiameter() != null ? settings.getNozzleDiameter() : 0.4));
            session.setLayerHeightMm(BigDecimal.valueOf(settings.getLayerHeight() != null ? settings.getLayerHeight() : 0.2));
            session.setInfillPattern(settings.getInfillPattern());
            session.setInfillPercent(settings.getInfillDensity() != null ? settings.getInfillDensity().intValue() : 20);
            session.setSupportsEnabled(settings.getSupportsEnabled() != null ? settings.getSupportsEnabled() : false);
            sessionRepo.save(session);

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
            }

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
            Map<String, String> processOverrides = new HashMap<>();
            if (settings.getLayerHeight() != null) processOverrides.put("layer_height", String.valueOf(settings.getLayerHeight()));
            if (settings.getInfillDensity() != null) processOverrides.put("sparse_infill_density", settings.getInfillDensity() + "%");
            if (settings.getInfillPattern() != null) processOverrides.put("sparse_infill_pattern", settings.getInfillPattern());
            
            // 3. Slice (Use persistent path)
            PrintStats stats = slicerService.slice(
                persistentPath.toFile(), 
                machineProfile, 
                filamentProfile, 
                processProfile, 
                null, // machine overrides
                processOverrides
            );

            Optional<ModelDimensions> modelDimensions = slicerService.inspectModelDimensions(persistentPath.toFile());
            
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
            
            item.setPrintTimeSeconds((int) stats.printTimeSeconds());
            item.setMaterialGrams(BigDecimal.valueOf(stats.filamentWeightGrams()));
            item.setUnitPriceChf(BigDecimal.valueOf(result.getTotalPrice()));
            
            // Store breakdown
            Map<String, Object> breakdown = new HashMap<>();
            breakdown.put("machine_cost", result.getTotalPrice()); // Excludes setup fee which is at session level
            breakdown.put("setup_fee", 0);
            item.setPricingBreakdown(breakdown);
            
            // Dimensions for shipping/package checks are computed server-side from the uploaded model.
            item.setBoundingBoxXMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.xMm()))
                    .orElseGet(() -> settings.getBoundingBoxX() != null ? BigDecimal.valueOf(settings.getBoundingBoxX()) : BigDecimal.ZERO));
            item.setBoundingBoxYMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.yMm()))
                    .orElseGet(() -> settings.getBoundingBoxY() != null ? BigDecimal.valueOf(settings.getBoundingBoxY()) : BigDecimal.ZERO));
            item.setBoundingBoxZMm(modelDimensions
                    .map(dim -> BigDecimal.valueOf(dim.zMm()))
                    .orElseGet(() -> settings.getBoundingBoxZ() != null ? BigDecimal.valueOf(settings.getBoundingBoxZ()) : BigDecimal.ZERO));
            
            item.setCreatedAt(OffsetDateTime.now());
            item.setUpdatedAt(OffsetDateTime.now());
            
            return lineItemRepo.save(item);

        } catch (Exception e) {
            // Cleanup if failed
            Files.deleteIfExists(persistentPath);
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
                    break;
                case "high":
                    settings.setLayerHeight(0.12);
                    settings.setInfillDensity(20.0);
                    settings.setInfillPattern("gyroid");
                    break;
                case "standard":
                default:
                    settings.setLayerHeight(0.20);
                    settings.setInfillDensity(15.0);
                    settings.setInfillPattern("grid");
                    break;
            }
        } else {
            // ADVANCED Mode: Use values from Frontend, set defaults if missing
            if (settings.getLayerHeight() == null) settings.setLayerHeight(0.20);
            if (settings.getInfillDensity() == null) settings.setInfillDensity(20.0);
            if (settings.getInfillPattern() == null) settings.setInfillPattern("grid");
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
        
        // Calculate Totals and global session hours
        BigDecimal itemsTotal = BigDecimal.ZERO;
        BigDecimal totalSeconds = BigDecimal.ZERO;

        for (QuoteLineItem item : items) {
            BigDecimal lineTotal = item.getUnitPriceChf().multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsTotal = itemsTotal.add(lineTotal);
            
            if (item.getPrintTimeSeconds() != null) {
                totalSeconds = totalSeconds.add(BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        
        BigDecimal totalHours = totalSeconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);
        com.printcalculator.entity.PricingPolicy policy = pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc();
        BigDecimal globalMachineCost = quoteCalculator.calculateSessionMachineCost(policy, totalHours);
        
        itemsTotal = itemsTotal.add(globalMachineCost);
        
        // Map items to DTO to embed distributed machine cost
        List<Map<String, Object>> itemsDto = new ArrayList<>();
        for (QuoteLineItem item : items) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", item.getId());
            dto.put("originalFilename", item.getOriginalFilename());
            dto.put("quantity", item.getQuantity());
            dto.put("printTimeSeconds", item.getPrintTimeSeconds());
            dto.put("materialGrams", item.getMaterialGrams());
            dto.put("colorCode", item.getColorCode());
            dto.put("status", item.getStatus());
            
            BigDecimal unitPrice = item.getUnitPriceChf();
            if (totalSeconds.compareTo(BigDecimal.ZERO) > 0 && item.getPrintTimeSeconds() != null) {
                BigDecimal itemSeconds = BigDecimal.valueOf(item.getPrintTimeSeconds()).multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal share = itemSeconds.divide(totalSeconds, 8, RoundingMode.HALF_UP);
                BigDecimal itemMachineCost = globalMachineCost.multiply(share);
                BigDecimal unitMachineCost = itemMachineCost.divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP);
                unitPrice = unitPrice.add(unitMachineCost);
            }
            dto.put("unitPriceChf", unitPrice);
            itemsDto.add(dto);
        }
        
        BigDecimal setupFee = session.getSetupCostChf() != null ? session.getSetupCostChf() : BigDecimal.ZERO;
        
        // Calculate shipping cost based on dimensions
        boolean exceedsBaseSize = false;
        for (QuoteLineItem item : items) {
            BigDecimal x = item.getBoundingBoxXMm() != null ? item.getBoundingBoxXMm() : BigDecimal.ZERO;
            BigDecimal y = item.getBoundingBoxYMm() != null ? item.getBoundingBoxYMm() : BigDecimal.ZERO;
            BigDecimal z = item.getBoundingBoxZMm() != null ? item.getBoundingBoxZMm() : BigDecimal.ZERO;
            
            BigDecimal[] dims = {x, y, z};
            java.util.Arrays.sort(dims);
            
            if (dims[2].compareTo(BigDecimal.valueOf(250.0)) > 0 ||
                dims[1].compareTo(BigDecimal.valueOf(176.0)) > 0 ||
                dims[0].compareTo(BigDecimal.valueOf(20.0)) > 0) {
                exceedsBaseSize = true;
                break;
            }
        }
        int totalQuantity = items.stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 1)
                .sum();

        BigDecimal shippingCostChf;
        if (exceedsBaseSize) {
            shippingCostChf = totalQuantity > 5 ? BigDecimal.valueOf(9.00) : BigDecimal.valueOf(4.00);
        } else {
            shippingCostChf = BigDecimal.valueOf(2.00);
        }

        BigDecimal grandTotal = itemsTotal.add(setupFee).add(shippingCostChf);
        
        Map<String, Object> response = new HashMap<>();
        response.put("session", session);
        response.put("items", itemsDto);
        response.put("itemsTotalChf", itemsTotal); // Includes the base cost of all items + the global tiered machine cost
        response.put("shippingCostChf", shippingCostChf);
        response.put("globalMachineCostChf", globalMachineCost); // Provide it so frontend knows how much it was (optional now)
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

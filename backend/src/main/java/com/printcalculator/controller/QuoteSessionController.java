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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quote-sessions")
@CrossOrigin(origins = "*") // Allow CORS for dev
public class QuoteSessionController {

    private final QuoteSessionRepository sessionRepo;
    private final QuoteLineItemRepository lineItemRepo;
    private final SlicerService slicerService;
    private final QuoteCalculator quoteCalculator;
    private final PrinterMachineRepository machineRepo;

    // Defaults
    private static final String DEFAULT_FILAMENT = "pla_basic";
    private static final String DEFAULT_PROCESS = "standard";

    public QuoteSessionController(QuoteSessionRepository sessionRepo,
                                  QuoteLineItemRepository lineItemRepo,
                                  SlicerService slicerService,
                                  QuoteCalculator quoteCalculator,
                                  PrinterMachineRepository machineRepo) {
        this.sessionRepo = sessionRepo;
        this.lineItemRepo = lineItemRepo;
        this.slicerService = slicerService;
        this.quoteCalculator = quoteCalculator;
        this.machineRepo = machineRepo;
    }

    // 1. Start a new session with a file
    @PostMapping(value = "/line-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<QuoteSession> createSessionAndAddItem(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // Create new session
        QuoteSession session = new QuoteSession();
        session.setStatus("ACTIVE");
        session.setPricingVersion("v1"); // Placeholder
        session.setMaterialCode(DEFAULT_FILAMENT); // Default for session
        session.setSupportsEnabled(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plusDays(30)); 
        // Set defaults
        session.setSetupCostChf(BigDecimal.ZERO);
        
        session = sessionRepo.save(session);

        // Process file and add item
        addItemToSession(session, file);
        
        // Refresh session to return updated data (if we added list fetching to repo, otherwise manually fetch items if needed for response)
        // For now, let's just return the session. The client might need to fetch items separately or we can return a DTO.
        // User request: "ritorna sessione + line items + total"
        // Since QuoteSession entity doesn't have a @OneToMany list of items (it has OneToMany usually but mapped by item), 
        // we might need a DTO or just rely on the fact that we might add the list to the entity if valid.
        // Looking at QuoteSession.java, it does NOT have a list of items.
        // So we should probably return a DTO or just return the Session and Client calls GET /quote-sessions/{id} immediately?
        // User request: "ritorna quoteSessionId" (actually implies just ID, but likely wants full object).
        // "ritorna sessione + line items + total (usa view o calcolo service)" refers to GET /quote-sessions/{id}
        
        // Let's return the full session details including items in a DTO/Map/wrapper?
        // Or just the session for now. The user said "ritorna quoteSessionId" for this specific endpoint.
        // Let's return the Session entity for now.
        return ResponseEntity.ok(session);
    }
    
    // 2. Add item to existing session
    @PostMapping(value = "/{id}/line-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<QuoteLineItem> addItemToExistingSession(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        QuoteSession session = sessionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        QuoteLineItem item = addItemToSession(session, file);
        return ResponseEntity.ok(item);
    }

    // Helper to add item
    private QuoteLineItem addItemToSession(QuoteSession session, MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IOException("File is empty");

        // 1. Save file temporarily
        Path tempInput = Files.createTempFile("upload_", "_" + file.getOriginalFilename());
        file.transferTo(tempInput.toFile());

        try {
            // 2. Mock Calc or Real Calc
            // The user said: "per ora calcolo mock" (mock calculation) but we have SlicerService. 
            // "Nota: il calcolo può essere stub: set print_time_seconds/material_grams/unit_price_chf a valori placeholder."
            // However, since we have the SlicerService, we CAN try to use it if we want, OR just use stub as requested to be fast?
            // "avvia calcolo (per ora calcolo mock)" -> I will use a simple Stub to satisfy the requirement immediately.
            // But I will also implement the structure to swap to Real Calc.
            
            // STUB CALCULATION as requested
            int printTime = 3600; // 1 hour
            BigDecimal materialGrams = new BigDecimal("50.00");
            BigDecimal unitPrice = new BigDecimal("15.00");
            
            // 3. Create Line Item
            QuoteLineItem item = new QuoteLineItem();
            item.setQuoteSession(session);
            item.setOriginalFilename(file.getOriginalFilename());
            item.setQuantity(1);
            item.setColorCode("#FFFFFF"); // Default
            item.setStatus("CALCULATED");
            
            item.setPrintTimeSeconds(printTime);
            item.setMaterialGrams(materialGrams);
            item.setUnitPriceChf(unitPrice);
            item.setPricingBreakdown(Map.of("mock", true));
            
            // Set simple bounding box
            item.setBoundingBoxXMm(BigDecimal.valueOf(100));
            item.setBoundingBoxYMm(BigDecimal.valueOf(100));
            item.setBoundingBoxZMm(BigDecimal.valueOf(20));
            
            item.setCreatedAt(OffsetDateTime.now());
            item.setUpdatedAt(OffsetDateTime.now());
            
            return lineItemRepo.save(item);

        } finally {
            Files.deleteIfExists(tempInput);
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
}

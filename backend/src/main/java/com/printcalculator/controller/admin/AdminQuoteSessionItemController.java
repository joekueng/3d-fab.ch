package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminQuoteItemStatsUpdateRequest;
import com.printcalculator.dto.AdminQuoteItemsResponse;
import com.printcalculator.service.admin.AdminQuoteItemPricingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quote-sessions")
public class AdminQuoteSessionItemController {

    private final AdminQuoteItemPricingService adminQuoteItemPricingService;

    public AdminQuoteSessionItemController(AdminQuoteItemPricingService adminQuoteItemPricingService) {
        this.adminQuoteItemPricingService = adminQuoteItemPricingService;
    }

    @GetMapping("/{sessionId}/items")
    public ResponseEntity<AdminQuoteItemsResponse> getSessionItems(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(adminQuoteItemPricingService.getItems(sessionId));
    }

    @PostMapping("/{sessionId}/items/print-stats")
    public ResponseEntity<AdminQuoteItemsResponse> updateSessionItemStats(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AdminQuoteItemStatsUpdateRequest request
    ) {
        return ResponseEntity.ok(adminQuoteItemPricingService.updateStats(sessionId, request));
    }
}

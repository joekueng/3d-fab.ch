package com.printcalculator.controller;

import com.printcalculator.dto.ServiceQuoteDtos.Customer;
import com.printcalculator.service.quote.CadCheckoutCustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/quote-sessions/{sessionId}/checkout-customer")
public class CadCheckoutCustomerController {
    private final CadCheckoutCustomerService service;
    public CadCheckoutCustomerController(CadCheckoutCustomerService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Customer> customer(@PathVariable UUID sessionId,
            @RequestHeader(value="X-Cad-Checkout-Token", required=false) String token) {
        return ServiceQuoteController.privateResponse(service.customer(sessionId, token));
    }
}

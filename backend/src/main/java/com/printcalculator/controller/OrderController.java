package com.printcalculator.controller;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;
    private final com.printcalculator.service.StorageService storageService;


    public OrderController(OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           QuoteSessionRepository quoteSessionRepo,
                           QuoteLineItemRepository quoteLineItemRepo,
                           CustomerRepository customerRepo,
                           com.printcalculator.service.StorageService storageService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
        this.storageService = storageService;
    }


    // 1. Create Order from Quote
    @PostMapping("/from-quote/{quoteSessionId}")
    @Transactional
    public ResponseEntity<Order> createOrderFromQuote(
            @PathVariable UUID quoteSessionId,
            @RequestBody com.printcalculator.dto.CreateOrderRequest request
    ) {
        // 1. Fetch Quote Session
        QuoteSession session = quoteSessionRepo.findById(quoteSessionId)
                .orElseThrow(() -> new RuntimeException("Quote Session not found"));

        if (!"ACTIVE".equals(session.getStatus())) {
             // Allow converting only active sessions? Or check if not already converted?
             // checking convertedOrderId might be better
        }
        if (session.getConvertedOrderId() != null) {
            return ResponseEntity.badRequest().body(null); // Already converted
        }

        // 2. Handle Customer (Find or Create)
        Customer customer = customerRepo.findByEmail(request.getCustomer().getEmail())
                .orElseGet(() -> {
                    Customer newC = new Customer();
                    newC.setEmail(request.getCustomer().getEmail());
                    newC.setCustomerType(request.getCustomer().getCustomerType());
                    newC.setCreatedAt(OffsetDateTime.now());
                    newC.setUpdatedAt(OffsetDateTime.now());
                    return customerRepo.save(newC);
                });
        // Update customer details?
        customer.setPhone(request.getCustomer().getPhone());
        customer.setCustomerType(request.getCustomer().getCustomerType());
        customer.setUpdatedAt(OffsetDateTime.now());
        customerRepo.save(customer);

        // 3. Create Order
        Order order = new Order();
        order.setSourceQuoteSession(session);
        order.setCustomer(customer);
        order.setCustomerEmail(request.getCustomer().getEmail());
        order.setCustomerPhone(request.getCustomer().getPhone());
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setCurrency("CHF");

        // Billing
        order.setBillingCustomerType(request.getCustomer().getCustomerType());
        if (request.getBillingAddress() != null) {
            order.setBillingFirstName(request.getBillingAddress().getFirstName());
            order.setBillingLastName(request.getBillingAddress().getLastName());
            order.setBillingCompanyName(request.getBillingAddress().getCompanyName());
            order.setBillingContactPerson(request.getBillingAddress().getContactPerson());
            order.setBillingAddressLine1(request.getBillingAddress().getAddressLine1());
            order.setBillingAddressLine2(request.getBillingAddress().getAddressLine2());
            order.setBillingZip(request.getBillingAddress().getZip());
            order.setBillingCity(request.getBillingAddress().getCity());
            order.setBillingCountryCode(request.getBillingAddress().getCountryCode() != null ? request.getBillingAddress().getCountryCode() : "CH");
        }

        // Shipping
        order.setShippingSameAsBilling(request.isShippingSameAsBilling());
        if (!request.isShippingSameAsBilling() && request.getShippingAddress() != null) {
             order.setShippingFirstName(request.getShippingAddress().getFirstName());
             order.setShippingLastName(request.getShippingAddress().getLastName());
             order.setShippingCompanyName(request.getShippingAddress().getCompanyName());
             order.setShippingContactPerson(request.getShippingAddress().getContactPerson());
             order.setShippingAddressLine1(request.getShippingAddress().getAddressLine1());
             order.setShippingAddressLine2(request.getShippingAddress().getAddressLine2());
             order.setShippingZip(request.getShippingAddress().getZip());
             order.setShippingCity(request.getShippingAddress().getCity());
             order.setShippingCountryCode(request.getShippingAddress().getCountryCode() != null ? request.getShippingAddress().getCountryCode() : "CH");
        } else {
            // Copy billing to shipping? Or leave empty and rely on flag?
            // Usually explicit copy is safer for queries
            order.setShippingFirstName(order.getBillingFirstName());
            order.setShippingLastName(order.getBillingLastName());
            order.setShippingCompanyName(order.getBillingCompanyName());
            order.setShippingContactPerson(order.getBillingContactPerson());
            order.setShippingAddressLine1(order.getBillingAddressLine1());
            order.setShippingAddressLine2(order.getBillingAddressLine2());
            order.setShippingZip(order.getBillingZip());
            order.setShippingCity(order.getBillingCity());
            order.setShippingCountryCode(order.getBillingCountryCode());
        }

        // Financials from Session (Assuming mocked/calculated in session)
        // We re-calculate totals from line items to be safe
        List<QuoteLineItem> quoteItems = quoteLineItemRepo.findByQuoteSessionId(quoteSessionId);
        
        BigDecimal subtotal = BigDecimal.ZERO;
        
        // Initialize financial fields to defaults to satisfy DB constraints
        order.setSubtotalChf(BigDecimal.ZERO);
        order.setTotalChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSetupCostChf(session.getSetupCostChf()); // Or 0 if null, but session has it
        order.setShippingCostChf(BigDecimal.valueOf(9.00)); // Default

        // Save Order first to get ID
        order = orderRepo.save(order);

        // 4. Create Order Items
        for (QuoteLineItem qItem : quoteItems) {
            OrderItem oItem = new OrderItem();
            oItem.setOrder(order);
            oItem.setOriginalFilename(qItem.getOriginalFilename());
            oItem.setQuantity(qItem.getQuantity());
            oItem.setColorCode(qItem.getColorCode());
            oItem.setMaterialCode(session.getMaterialCode()); // Or per item if supported
            
            // Pricing
            oItem.setUnitPriceChf(qItem.getUnitPriceChf());
            oItem.setLineTotalChf(qItem.getUnitPriceChf().multiply(BigDecimal.valueOf(qItem.getQuantity())));
            oItem.setPrintTimeSeconds(qItem.getPrintTimeSeconds());
            oItem.setMaterialGrams(qItem.getMaterialGrams());
            
            // File Handling Check
            // "orders/{orderId}/3d-files/{orderItemId}/{uuid}.{ext}"
            UUID fileUuid = UUID.randomUUID();
            String ext = getExtension(qItem.getOriginalFilename());
            String storedFilename = fileUuid.toString() + "." + ext;
            
            oItem.setStoredFilename(storedFilename);
            oItem.setStoredRelativePath("PENDING"); // Placeholder
            oItem.setMimeType("application/octet-stream"); // specific type if known
            oItem.setCreatedAt(OffsetDateTime.now());
            
            oItem = orderItemRepo.save(oItem);
            
            // Update Path now that we have ID
            String relativePath = "orders/" + order.getId() + "/3d-files/" + oItem.getId() + "/" + storedFilename;
            oItem.setStoredRelativePath(relativePath);
            
            // COPY FILE from Quote to Order
            if (qItem.getStoredPath() != null) {
                try {
                    Path sourcePath = Paths.get(qItem.getStoredPath());
                    if (Files.exists(sourcePath)) {
                        storageService.store(sourcePath, Paths.get(relativePath));
                        
                        oItem.setFileSizeBytes(Files.size(sourcePath));
                    }
                } catch (IOException e) {
                    e.printStackTrace(); // Log error but allow order creation? Or fail?
                    // Ideally fail or mark as error
                }
            }
            
            orderItemRepo.save(oItem);
            
            subtotal = subtotal.add(oItem.getLineTotalChf());
        }

        // Update Order Totals
        order.setSubtotalChf(subtotal);
        order.setSetupCostChf(session.getSetupCostChf());
        order.setShippingCostChf(BigDecimal.valueOf(9.00)); // Default shipping? or 0?
        order.setDiscountChf(BigDecimal.ZERO);
        // Calculate Shipping (Basic implementation: Flat rate 9.00 if not pickup)
        // Future: Check delivery method from request if available
        if (order.getShippingCostChf() == null) {
             order.setShippingCostChf(BigDecimal.valueOf(9.00));
        }
        
        BigDecimal total = subtotal.add(order.getSetupCostChf()).add(order.getShippingCostChf()).subtract(order.getDiscountChf() != null ? order.getDiscountChf() : BigDecimal.ZERO);
        order.setTotalChf(total);
        
        // Link session
        session.setConvertedOrderId(order.getId());
        session.setStatus("CONVERTED"); // or CLOSED
        quoteSessionRepo.save(session);
        
        return ResponseEntity.ok(orderRepo.save(order));
    }
    
    // 2. Upload file for Order Item
    @PostMapping(value = "/{orderId}/items/{orderItemId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Void> uploadOrderItemFile(
        @PathVariable UUID orderId,
        @PathVariable UUID orderItemId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        
        OrderItem item = orderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new RuntimeException("OrderItem not found"));
                
        if (!item.getOrder().getId().equals(orderId)) {
            return ResponseEntity.badRequest().build();
        }
        
        // Ensure path logic
        String relativePath = item.getStoredRelativePath();
        if (relativePath == null || relativePath.equals("PENDING")) {
             // Should verify consistency
             // If we used the logic above, it should have a path.
             // If it's "PENDING", regen it.
             String ext = getExtension(file.getOriginalFilename());
             String storedFilename = UUID.randomUUID().toString() + "." + ext;
             relativePath = "orders/" + orderId + "/3d-files/" + orderItemId + "/" + storedFilename;
             item.setStoredRelativePath(relativePath);
             item.setStoredFilename(storedFilename);
             // Update item
        }
        
        // Save file to disk
        storageService.store(file, Paths.get(relativePath));
        
        item.setFileSizeBytes(file.getSize());
        item.setMimeType(file.getContentType());
        // Calculate SHA256? (Optional)
        
        orderItemRepo.save(item);
        
        return ResponseEntity.ok().build();
    }
    
    private String getExtension(String filename) {
        if (filename == null) return "stl";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1);
        }
        return "stl";
    }

}

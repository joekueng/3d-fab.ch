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
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;

    // TODO: Inject Storage Service or use a base path property
    private static final String STORAGE_ROOT = "storage_orders"; 

    public OrderController(OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           QuoteSessionRepository quoteSessionRepo,
                           QuoteLineItemRepository quoteLineItemRepo,
                           CustomerRepository customerRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
    }

    // DTOs
    public static class CreateOrderRequest {
        public CustomerDto customer;
        public AddressDto billingAddress;
        public AddressDto shippingAddress;
        public boolean shippingSameAsBilling;
    }

    public static class CustomerDto {
        public String email;
        public String phone;
        public String customerType; // "PRIVATE", "BUSINESS"
    }

    public static class AddressDto {
        public String firstName;
        public String lastName;
        public String companyName;
        public String contactPerson;
        public String addressLine1;
        public String addressLine2;
        public String zip;
        public String city;
        public String countryCode;
    }

    // 1. Create Order from Quote
    @PostMapping("/from-quote/{quoteSessionId}")
    @Transactional
    public ResponseEntity<Order> createOrderFromQuote(
            @PathVariable UUID quoteSessionId,
            @RequestBody CreateOrderRequest request
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
        Customer customer = customerRepo.findByEmail(request.customer.email)
                .orElseGet(() -> {
                    Customer newC = new Customer();
                    newC.setEmail(request.customer.email);
                    newC.setCreatedAt(OffsetDateTime.now());
                    return customerRepo.save(newC);
                });
        // Update customer details?
        customer.setPhone(request.customer.phone);
        customer.setCustomerType(request.customer.customerType);
        customer.setUpdatedAt(OffsetDateTime.now());
        customerRepo.save(customer);

        // 3. Create Order
        Order order = new Order();
        order.setSourceQuoteSession(session);
        order.setCustomer(customer);
        order.setCustomerEmail(request.customer.email);
        order.setCustomerPhone(request.customer.phone);
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setCurrency("CHF");

        // Billing
        order.setBillingCustomerType(request.customer.customerType);
        if (request.billingAddress != null) {
            order.setBillingFirstName(request.billingAddress.firstName);
            order.setBillingLastName(request.billingAddress.lastName);
            order.setBillingCompanyName(request.billingAddress.companyName);
            order.setBillingContactPerson(request.billingAddress.contactPerson);
            order.setBillingAddressLine1(request.billingAddress.addressLine1);
            order.setBillingAddressLine2(request.billingAddress.addressLine2);
            order.setBillingZip(request.billingAddress.zip);
            order.setBillingCity(request.billingAddress.city);
            order.setBillingCountryCode(request.billingAddress.countryCode != null ? request.billingAddress.countryCode : "CH");
        }

        // Shipping
        order.setShippingSameAsBilling(request.shippingSameAsBilling);
        if (!request.shippingSameAsBilling && request.shippingAddress != null) {
             order.setShippingFirstName(request.shippingAddress.firstName);
             order.setShippingLastName(request.shippingAddress.lastName);
             order.setShippingCompanyName(request.shippingAddress.companyName);
             order.setShippingContactPerson(request.shippingAddress.contactPerson);
             order.setShippingAddressLine1(request.shippingAddress.addressLine1);
             order.setShippingAddressLine2(request.shippingAddress.addressLine2);
             order.setShippingZip(request.shippingAddress.zip);
             order.setShippingCity(request.shippingAddress.city);
             order.setShippingCountryCode(request.shippingAddress.countryCode != null ? request.shippingAddress.countryCode : "CH");
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
            
            // Note: We don't have the orderItemId yet because we haven't saved it. 
            // We can pre-generate ID or save order item then update path?
            // GeneratedValue strategy AUTO might not let us set ID easily?
            // Let's save item first with temporary path, then update?
            // OR use a path structure that doesn't depend on ItemId? "orders/{orderId}/3d-files/{uuid}.ext" is also fine?
            // User requested: "orders/{orderId}/3d-files/{orderItemId}/{uuid}.{ext}"
            // So we need OrderItemId.
            
            oItem.setStoredFilename(storedFilename);
            oItem.setStoredRelativePath("PENDING"); // Placeholder
            oItem.setMimeType("application/octet-stream"); // specific type if known
            
            oItem = orderItemRepo.save(oItem);
            
            // Update Path now that we have ID
            String relativePath = "orders/" + order.getId() + "/3d-files/" + oItem.getId() + "/" + storedFilename;
            oItem.setStoredRelativePath(relativePath);
            orderItemRepo.save(oItem);
            
            subtotal = subtotal.add(oItem.getLineTotalChf());
        }

        // Update Order Totals
        order.setSubtotalChf(subtotal);
        order.setSetupCostChf(session.getSetupCostChf());
        order.setShippingCostChf(BigDecimal.valueOf(9.00)); // Default shipping? or 0?
        // TODO: Calc implementation for shipping
        
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
        Path absolutePath = Paths.get(STORAGE_ROOT, relativePath);
        Files.createDirectories(absolutePath.getParent());
        
        if (Files.exists(absolutePath)) {
            Files.delete(absolutePath); // Overwrite?
        }
        
        Files.copy(file.getInputStream(), absolutePath);
        
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

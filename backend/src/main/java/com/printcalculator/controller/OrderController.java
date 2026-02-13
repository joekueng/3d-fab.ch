package com.printcalculator.controller;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.InvoicePdfRenderingService;
import com.printcalculator.service.StorageService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;


    public OrderController(OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           QuoteSessionRepository quoteSessionRepo,
                           QuoteLineItemRepository quoteLineItemRepo,
                           CustomerRepository customerRepo,
                           StorageService storageService,
                           InvoicePdfRenderingService invoiceService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
    }


    // 1. Create Order from Quote
    @PostMapping("/from-quote/{quoteSessionId}")
    @Transactional
    public ResponseEntity<Order> createOrderFromQuote(
            @PathVariable UUID quoteSessionId,
            @RequestBody com.printcalculator.dto.CreateOrderRequest request
    ) {
        QuoteSession session = quoteSessionRepo.findById(quoteSessionId)
                .orElseThrow(() -> new RuntimeException("Quote Session not found"));

        if (session.getConvertedOrderId() != null) {
            return ResponseEntity.badRequest().body(null); 
        }

        Customer customer = customerRepo.findByEmail(request.getCustomer().getEmail())
                .orElseGet(() -> {
                    Customer newC = new Customer();
                    newC.setEmail(request.getCustomer().getEmail());
                    newC.setCustomerType(request.getCustomer().getCustomerType());
                    newC.setCreatedAt(OffsetDateTime.now());
                    newC.setUpdatedAt(OffsetDateTime.now());
                    return customerRepo.save(newC);
                });
        
        customer.setPhone(request.getCustomer().getPhone());
        customer.setCustomerType(request.getCustomer().getCustomerType());
        customer.setUpdatedAt(OffsetDateTime.now());
        customerRepo.save(customer);

        Order order = new Order();
        order.setSourceQuoteSession(session);
        order.setCustomer(customer);
        order.setCustomerEmail(request.getCustomer().getEmail());
        order.setCustomerPhone(request.getCustomer().getPhone());
        order.setStatus("PENDING_PAYMENT");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setCurrency("CHF");

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

        List<QuoteLineItem> quoteItems = quoteLineItemRepo.findByQuoteSessionId(quoteSessionId);
        
        BigDecimal subtotal = BigDecimal.ZERO;
        order.setSubtotalChf(BigDecimal.ZERO);
        order.setTotalChf(BigDecimal.ZERO);
        order.setDiscountChf(BigDecimal.ZERO);
        order.setSetupCostChf(session.getSetupCostChf() != null ? session.getSetupCostChf() : BigDecimal.ZERO); 
        order.setShippingCostChf(BigDecimal.valueOf(9.00)); 

        order = orderRepo.save(order);

        for (QuoteLineItem qItem : quoteItems) {
            OrderItem oItem = new OrderItem();
            oItem.setOrder(order);
            oItem.setOriginalFilename(qItem.getOriginalFilename());
            oItem.setQuantity(qItem.getQuantity());
            oItem.setColorCode(qItem.getColorCode());
            oItem.setMaterialCode(session.getMaterialCode());
            
            oItem.setUnitPriceChf(qItem.getUnitPriceChf());
            oItem.setLineTotalChf(qItem.getUnitPriceChf().multiply(BigDecimal.valueOf(qItem.getQuantity())));
            oItem.setPrintTimeSeconds(qItem.getPrintTimeSeconds());
            oItem.setMaterialGrams(qItem.getMaterialGrams());
            
            UUID fileUuid = UUID.randomUUID();
            String ext = getExtension(qItem.getOriginalFilename());
            String storedFilename = fileUuid.toString() + "." + ext;
            
            oItem.setStoredFilename(storedFilename);
            oItem.setStoredRelativePath("PENDING"); 
            oItem.setMimeType("application/octet-stream"); 
            oItem.setCreatedAt(OffsetDateTime.now());
            
            oItem = orderItemRepo.save(oItem);
            
            String relativePath = "orders/" + order.getId() + "/3d-files/" + oItem.getId() + "/" + storedFilename;
            oItem.setStoredRelativePath(relativePath);
            
            if (qItem.getStoredPath() != null) {
                try {
                    Path sourcePath = Paths.get(qItem.getStoredPath());
                    if (Files.exists(sourcePath)) {
                        storageService.store(sourcePath, Paths.get(relativePath));
                        oItem.setFileSizeBytes(Files.size(sourcePath));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            orderItemRepo.save(oItem);
            subtotal = subtotal.add(oItem.getLineTotalChf());
        }

        order.setSubtotalChf(subtotal);
        if (order.getShippingCostChf() == null) {
             order.setShippingCostChf(BigDecimal.valueOf(9.00));
        }
        
        BigDecimal total = subtotal.add(order.getSetupCostChf()).add(order.getShippingCostChf()).subtract(order.getDiscountChf() != null ? order.getDiscountChf() : BigDecimal.ZERO);
        order.setTotalChf(total);
        
        session.setConvertedOrderId(order.getId());
        session.setStatus("CONVERTED"); 
        quoteSessionRepo.save(session);
        
        return ResponseEntity.ok(orderRepo.save(order));
    }
    
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
        
        String relativePath = item.getStoredRelativePath();
        if (relativePath == null || relativePath.equals("PENDING")) {
             String ext = getExtension(file.getOriginalFilename());
             String storedFilename = UUID.randomUUID().toString() + "." + ext;
             relativePath = "orders/" + orderId + "/3d-files/" + orderItemId + "/" + storedFilename;
             item.setStoredRelativePath(relativePath);
             item.setStoredFilename(storedFilename);
        }
        
        storageService.store(file, Paths.get(relativePath));
        item.setFileSizeBytes(file.getSize());
        item.setMimeType(file.getContentType());
        orderItemRepo.save(item);
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID orderId) {
        return orderRepo.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<byte[]> getInvoice(@PathVariable UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderItem> items = orderItemRepo.findByOrder_Id(orderId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("sellerDisplayName", "3D Fab Switzerland");
        vars.put("sellerAddressLine1", "Sede Ticino, Svizzera");
        vars.put("sellerAddressLine2", "Sede Bienne, Svizzera");
        vars.put("sellerEmail", "info@3dfab.ch");

        vars.put("invoiceNumber", "INV-" + order.getId().toString().substring(0, 8).toUpperCase());
        vars.put("invoiceDate", order.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        vars.put("dueDate", order.getCreatedAt().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE));

        String buyerName = order.getBillingCustomerType().equals("BUSINESS") 
            ? order.getBillingCompanyName() 
            : order.getBillingFirstName() + " " + order.getBillingLastName();
        vars.put("buyerDisplayName", buyerName);
        vars.put("buyerAddressLine1", order.getBillingAddressLine1());
        vars.put("buyerAddressLine2", order.getBillingZip() + " " + order.getBillingCity() + ", " + order.getBillingCountryCode());

        List<Map<String, Object>> invoiceLineItems = items.stream().map(i -> {
            Map<String, Object> line = new HashMap<>();
            line.put("description", "Stampa 3D: " + i.getOriginalFilename());
            line.put("quantity", i.getQuantity());
            line.put("unitPriceFormatted", String.format("CHF %.2f", i.getUnitPriceChf()));
            line.put("lineTotalFormatted", String.format("CHF %.2f", i.getLineTotalChf()));
            return line;
        }).collect(Collectors.toList());

        // Add Setup and Shipping as line items too? Or separate in template?
        // Template has invoiceLineItems loop. Let's add them there for simplicity or separate.
        // Let's add them to the list.
        Map<String, Object> setupLine = new HashMap<>();
        setupLine.put("description", "Costo Setup");
        setupLine.put("quantity", 1);
        setupLine.put("unitPriceFormatted", String.format("CHF %.2f", order.getSetupCostChf()));
        setupLine.put("lineTotalFormatted", String.format("CHF %.2f", order.getSetupCostChf()));
        invoiceLineItems.add(setupLine);

        Map<String, Object> shippingLine = new HashMap<>();
        shippingLine.put("description", "Spedizione");
        shippingLine.put("quantity", 1);
        shippingLine.put("unitPriceFormatted", String.format("CHF %.2f", order.getShippingCostChf()));
        shippingLine.put("lineTotalFormatted", String.format("CHF %.2f", order.getShippingCostChf()));
        invoiceLineItems.add(shippingLine);

        vars.put("invoiceLineItems", invoiceLineItems);
        vars.put("subtotalFormatted", String.format("CHF %.2f", order.getSubtotalChf()));
        vars.put("grandTotalFormatted", String.format("CHF %.2f", order.getTotalChf()));
        vars.put("paymentTermsText", "Pagamento entro 7 giorni via Bonifico o TWINT. Grazie.");

        byte[] pdf = invoiceService.generateInvoicePdfBytesFromTemplate(vars);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"invoice-" + orderId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
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

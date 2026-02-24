package com.printcalculator.controller;

import com.printcalculator.dto.*;
import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.InvoicePdfRenderingService;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.PaymentService;
import com.printcalculator.service.QrBillService;
import com.printcalculator.service.StorageService;
import com.printcalculator.service.TwintPaymentService;
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
import java.util.Base64;
import java.util.stream.Collectors;
import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final TwintPaymentService twintPaymentService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepo;


    public OrderController(OrderService orderService,
                           OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           QuoteSessionRepository quoteSessionRepo,
                           QuoteLineItemRepository quoteLineItemRepo,
                           CustomerRepository customerRepo,
                           StorageService storageService,
                           InvoicePdfRenderingService invoiceService,
                           QrBillService qrBillService,
                           TwintPaymentService twintPaymentService,
                           PaymentService paymentService,
                           PaymentRepository paymentRepo) {
        this.orderService = orderService;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.twintPaymentService = twintPaymentService;
        this.paymentService = paymentService;
        this.paymentRepo = paymentRepo;
    }


    // 1. Create Order from Quote
    @PostMapping("/from-quote/{quoteSessionId}")
    @Transactional
    public ResponseEntity<OrderDto> createOrderFromQuote(
            @PathVariable UUID quoteSessionId,
            @RequestBody com.printcalculator.dto.CreateOrderRequest request
    ) {
        Order order = orderService.createOrderFromQuote(quoteSessionId, request);
        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
        return ResponseEntity.ok(convertToDto(order, items));
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
    public ResponseEntity<OrderDto> getOrder(@PathVariable UUID orderId) {
        return orderRepo.findById(orderId)
                .map(o -> {
                    List<OrderItem> items = orderItemRepo.findByOrder_Id(o.getId());
                    return ResponseEntity.ok(convertToDto(o, items));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/payments/report")
    @Transactional
    public ResponseEntity<OrderDto> reportPayment(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> payload
    ) {
        String method = payload.get("method");
        paymentService.reportPayment(orderId, method);
        return getOrder(orderId);
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

        vars.put("invoiceNumber", "INV-" + getDisplayOrderNumber(order).toUpperCase());
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

        String qrBillSvg = new String(qrBillService.generateQrBillSvg(order), java.nio.charset.StandardCharsets.UTF_8);
        
        // Strip XML declaration and DOCTYPE if present, as they validity break the embedding HTML page
        if (qrBillSvg.contains("<?xml")) {
            int svgStartIndex = qrBillSvg.indexOf("<svg");
            if (svgStartIndex != -1) {
                qrBillSvg = qrBillSvg.substring(svgStartIndex);
            }
        }
        
        byte[] pdf = invoiceService.generateInvoicePdfBytesFromTemplate(vars, qrBillSvg);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"invoice-" + getDisplayOrderNumber(order) + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/{orderId}/twint")
    public ResponseEntity<Map<String, String>> getTwintPayment(@PathVariable UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] qrPng = twintPaymentService.generateQrPng(order, 360);
        String qrDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng);

        Map<String, String> data = new HashMap<>();
        data.put("paymentUrl", twintPaymentService.getTwintPaymentUrl(order));
        data.put("openUrl", "/api/orders/" + orderId + "/twint/open");
        data.put("qrImageUrl", "/api/orders/" + orderId + "/twint/qr");
        data.put("qrImageDataUri", qrDataUri);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{orderId}/twint/open")
    public ResponseEntity<Void> openTwintPayment(@PathVariable UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302)
                .location(URI.create(twintPaymentService.getTwintPaymentUrl(order)))
                .build();
    }

    @GetMapping("/{orderId}/twint/qr")
    public ResponseEntity<byte[]> getTwintQr(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "320") int size
    ) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        int normalizedSize = Math.max(200, Math.min(size, 600));
        byte[] png = twintPaymentService.generateQrPng(order, normalizedSize);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
    
    private String getExtension(String filename) {
        if (filename == null) return "stl";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1);
        }
        return "stl";
    }

    private OrderDto convertToDto(Order order, List<OrderItem> items) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(getDisplayOrderNumber(order));
        dto.setStatus(order.getStatus());

        paymentRepo.findByOrder_Id(order.getId()).ifPresent(p -> {
            dto.setPaymentStatus(p.getStatus());
            dto.setPaymentMethod(p.getMethod());
        });

        dto.setCustomerEmail(order.getCustomerEmail());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setBillingCustomerType(order.getBillingCustomerType());
        dto.setCurrency(order.getCurrency());
        dto.setSetupCostChf(order.getSetupCostChf());
        dto.setShippingCostChf(order.getShippingCostChf());
        dto.setDiscountChf(order.getDiscountChf());
        dto.setSubtotalChf(order.getSubtotalChf());
        dto.setTotalChf(order.getTotalChf());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setShippingSameAsBilling(order.getShippingSameAsBilling());

        AddressDto billing = new AddressDto();
        billing.setFirstName(order.getBillingFirstName());
        billing.setLastName(order.getBillingLastName());
        billing.setCompanyName(order.getBillingCompanyName());
        billing.setContactPerson(order.getBillingContactPerson());
        billing.setAddressLine1(order.getBillingAddressLine1());
        billing.setAddressLine2(order.getBillingAddressLine2());
        billing.setZip(order.getBillingZip());
        billing.setCity(order.getBillingCity());
        billing.setCountryCode(order.getBillingCountryCode());
        dto.setBillingAddress(billing);

        if (!order.getShippingSameAsBilling()) {
            AddressDto shipping = new AddressDto();
            shipping.setFirstName(order.getShippingFirstName());
            shipping.setLastName(order.getShippingLastName());
            shipping.setCompanyName(order.getShippingCompanyName());
            shipping.setContactPerson(order.getShippingContactPerson());
            shipping.setAddressLine1(order.getShippingAddressLine1());
            shipping.setAddressLine2(order.getShippingAddressLine2());
            shipping.setZip(order.getShippingZip());
            shipping.setCity(order.getShippingCity());
            shipping.setCountryCode(order.getShippingCountryCode());
            dto.setShippingAddress(shipping);
        }

        List<OrderItemDto> itemDtos = items.stream().map(i -> {
            OrderItemDto idto = new OrderItemDto();
            idto.setId(i.getId());
            idto.setOriginalFilename(i.getOriginalFilename());
            idto.setMaterialCode(i.getMaterialCode());
            idto.setColorCode(i.getColorCode());
            idto.setQuantity(i.getQuantity());
            idto.setPrintTimeSeconds(i.getPrintTimeSeconds());
            idto.setMaterialGrams(i.getMaterialGrams());
            idto.setUnitPriceChf(i.getUnitPriceChf());
            idto.setLineTotalChf(i.getLineTotalChf());
            return idto;
        }).collect(Collectors.toList());
        dto.setItems(itemDtos);

        return dto;
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

}

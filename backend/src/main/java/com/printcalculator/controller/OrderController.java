package com.printcalculator.controller;

import com.printcalculator.dto.*;
import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import com.printcalculator.service.payment.TwintPaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.IOException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Set<String> PERSONAL_DATA_REDACTED_STATUSES = Set.of(
            "IN_PRODUCTION",
            "SHIPPED",
            "COMPLETED"
    );

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
            @Valid @RequestBody com.printcalculator.dto.CreateOrderRequest request
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
        Path destinationRelativePath;
        if (relativePath == null || relativePath.equals("PENDING")) {
             String ext = getExtension(file.getOriginalFilename());
             String storedFilename = UUID.randomUUID() + "." + ext;
             destinationRelativePath = Path.of("orders", orderId.toString(), "3d-files", orderItemId.toString(), storedFilename);
             item.setStoredRelativePath(destinationRelativePath.toString());
             item.setStoredFilename(storedFilename);
        } else {
            destinationRelativePath = resolveOrderItemRelativePath(relativePath, orderId, orderItemId);
            if (destinationRelativePath == null) {
                return ResponseEntity.badRequest().build();
            }
        }

        storageService.store(file, destinationRelativePath);
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

    @GetMapping("/{orderId}/confirmation")
    public ResponseEntity<byte[]> getConfirmation(@PathVariable UUID orderId) {
        return generateDocument(orderId, true);
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<byte[]> getInvoice(@PathVariable UUID orderId) {
        // Paid invoices are sent by email after back-office payment confirmation.
        // The public endpoint must not expose a "paid" invoice download.
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<byte[]> generateDocument(UUID orderId, boolean isConfirmation) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (isConfirmation) {
            Path relativePath = buildConfirmationPdfRelativePath(order);
            try {
                byte[] existingPdf = storageService.loadAsResource(relativePath).getInputStream().readAllBytes();
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"confirmation-" + getDisplayOrderNumber(order) + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(existingPdf);
            } catch (Exception ignored) {
                // Fallback to on-the-fly generation if the stored file is missing or unreadable.
            }
        }

        List<OrderItem> items = orderItemRepo.findByOrder_Id(orderId);
        Payment payment = paymentRepo.findByOrder_Id(orderId).orElse(null);

        byte[] pdf = invoiceService.generateDocumentPdf(order, items, isConfirmation, qrBillService, payment);
        String typePrefix = isConfirmation ? "confirmation-" : "invoice-";
        String truncatedUuid = order.getId().toString().substring(0, 8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + typePrefix + truncatedUuid + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Path buildConfirmationPdfRelativePath(Order order) {
        return Path.of(
                "orders",
                order.getId().toString(),
                "documents",
                "confirmation-" + getDisplayOrderNumber(order) + ".pdf"
        );
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
        String cleaned = StringUtils.cleanPath(filename);
        if (cleaned.contains("..")) {
            return "stl";
        }
        int i = cleaned.lastIndexOf('.');
        if (i > 0 && i < cleaned.length() - 1) {
            String ext = cleaned.substring(i + 1).toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSION_PATTERN.matcher(ext).matches()) {
                return ext;
            }
        }
        return "stl";
    }

    private Path resolveOrderItemRelativePath(String storedRelativePath, UUID orderId, UUID orderItemId) {
        try {
            Path candidate = Path.of(storedRelativePath).normalize();
            if (candidate.isAbsolute()) {
                return null;
            }

            Path expectedPrefix = Path.of("orders", orderId.toString(), "3d-files", orderItemId.toString());
            if (!candidate.startsWith(expectedPrefix)) {
                return null;
            }

            return candidate;
        } catch (InvalidPathException e) {
            return null;
        }
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

        boolean redactPersonalData = shouldRedactPersonalData(order.getStatus());
        if (!redactPersonalData) {
            dto.setCustomerEmail(order.getCustomerEmail());
            dto.setCustomerPhone(order.getCustomerPhone());
            dto.setBillingCustomerType(order.getBillingCustomerType());
        }
        dto.setPreferredLanguage(order.getPreferredLanguage());
        dto.setCurrency(order.getCurrency());
        dto.setSetupCostChf(order.getSetupCostChf());
        dto.setShippingCostChf(order.getShippingCostChf());
        dto.setDiscountChf(order.getDiscountChf());
        dto.setSubtotalChf(order.getSubtotalChf());
        dto.setIsCadOrder(order.getIsCadOrder());
        dto.setSourceRequestId(order.getSourceRequestId());
        dto.setCadHours(order.getCadHours());
        dto.setCadHourlyRateChf(order.getCadHourlyRateChf());
        dto.setCadTotalChf(order.getCadTotalChf());
        dto.setTotalChf(order.getTotalChf());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setShippingSameAsBilling(order.getShippingSameAsBilling());

        if (!redactPersonalData) {
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

    private boolean shouldRedactPersonalData(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return PERSONAL_DATA_REDACTED_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }

}

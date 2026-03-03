package com.printcalculator.controller.admin;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.AdminOrderStatusUpdateRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.dto.OrderItemDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.InvoicePdfRenderingService;
import com.printcalculator.service.PaymentService;
import com.printcalculator.service.QrBillService;
import com.printcalculator.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin/orders")
@Transactional(readOnly = true)
public class AdminOrderController {
    private static final List<String> ALLOWED_ORDER_STATUSES = List.of(
            "PENDING_PAYMENT",
            "PAID",
            "IN_PRODUCTION",
            "SHIPPED",
            "COMPLETED",
            "CANCELLED"
    );

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentService paymentService;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;

    public AdminOrderController(
            OrderRepository orderRepo,
            OrderItemRepository orderItemRepo,
            PaymentRepository paymentRepo,
            PaymentService paymentService,
            StorageService storageService,
            InvoicePdfRenderingService invoiceService,
            QrBillService qrBillService
    ) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.paymentService = paymentService;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> listOrders() {
        List<OrderDto> response = orderRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(toOrderDto(getOrderOrThrow(orderId)));
    }

    @PostMapping("/{orderId}/payments/confirm")
    @Transactional
    public ResponseEntity<OrderDto> confirmPayment(
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        getOrderOrThrow(orderId);
        String method = payload != null ? payload.get("method") : null;
        paymentService.confirmPayment(orderId, method);
        return ResponseEntity.ok(toOrderDto(getOrderOrThrow(orderId)));
    }

    @PostMapping("/{orderId}/status")
    @Transactional
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody AdminOrderStatusUpdateRequest payload
    ) {
        if (payload == null || payload.getStatus() == null || payload.getStatus().isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Status is required");
        }

        Order order = getOrderOrThrow(orderId);
        String normalizedStatus = payload.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ORDER_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid order status. Allowed values: " + String.join(", ", ALLOWED_ORDER_STATUSES)
            );
        }
        order.setStatus(normalizedStatus);
        orderRepo.save(order);

        return ResponseEntity.ok(toOrderDto(order));
    }

    @GetMapping("/{orderId}/items/{orderItemId}/file")
    public ResponseEntity<Resource> downloadOrderItemFile(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId
    ) {
        OrderItem item = orderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order item not found"));

        if (!item.getOrder().getId().equals(orderId)) {
            throw new ResponseStatusException(NOT_FOUND, "Order item not found for order");
        }

        String relativePath = item.getStoredRelativePath();
        if (relativePath == null || relativePath.isBlank() || "PENDING".equals(relativePath)) {
            throw new ResponseStatusException(NOT_FOUND, "File not available");
        }
        Path safeRelativePath = resolveOrderItemRelativePath(relativePath, orderId, orderItemId);
        if (safeRelativePath == null) {
            throw new ResponseStatusException(NOT_FOUND, "File not available");
        }

        try {
            Resource resource = storageService.loadAsResource(safeRelativePath);
            MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
            if (item.getMimeType() != null && !item.getMimeType().isBlank()) {
                try {
                    contentType = MediaType.parseMediaType(item.getMimeType());
                } catch (Exception ignored) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM;
                }
            }

            String filename = item.getOriginalFilename() != null && !item.getOriginalFilename().isBlank()
                    ? item.getOriginalFilename()
                    : "order-item-" + orderItemId;

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "File not available");
        }
    }

    @GetMapping("/{orderId}/documents/confirmation")
    public ResponseEntity<byte[]> downloadOrderConfirmation(@PathVariable UUID orderId) {
        return generateDocument(getOrderOrThrow(orderId), true);
    }

    @GetMapping("/{orderId}/documents/invoice")
    public ResponseEntity<byte[]> downloadOrderInvoice(@PathVariable UUID orderId) {
        return generateDocument(getOrderOrThrow(orderId), false);
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
    }

    private OrderDto toOrderDto(Order order) {
        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
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
        dto.setPreferredLanguage(order.getPreferredLanguage());
        dto.setBillingCustomerType(order.getBillingCustomerType());
        dto.setCurrency(order.getCurrency());
        dto.setSetupCostChf(order.getSetupCostChf());
        dto.setShippingCostChf(order.getShippingCostChf());
        dto.setDiscountChf(order.getDiscountChf());
        dto.setSubtotalChf(order.getSubtotalChf());
        dto.setTotalChf(order.getTotalChf());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setShippingSameAsBilling(order.getShippingSameAsBilling());
        QuoteSession sourceSession = order.getSourceQuoteSession();
        if (sourceSession != null) {
            dto.setPrintMaterialCode(sourceSession.getMaterialCode());
            dto.setPrintNozzleDiameterMm(sourceSession.getNozzleDiameterMm());
            dto.setPrintLayerHeightMm(sourceSession.getLayerHeightMm());
            dto.setPrintInfillPattern(sourceSession.getInfillPattern());
            dto.setPrintInfillPercent(sourceSession.getInfillPercent());
            dto.setPrintSupportsEnabled(sourceSession.getSupportsEnabled());
        }

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

        if (!Boolean.TRUE.equals(order.getShippingSameAsBilling())) {
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
        }).toList();
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

    private ResponseEntity<byte[]> generateDocument(Order order, boolean isConfirmation) {
        String displayOrderNumber = getDisplayOrderNumber(order);
        if (isConfirmation) {
            Path relativePath = buildConfirmationPdfRelativePath(order.getId(), displayOrderNumber);
            try {
                byte[] existingPdf = storageService.loadAsResource(relativePath).getInputStream().readAllBytes();
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"confirmation-" + displayOrderNumber + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(existingPdf);
            } catch (Exception ignored) {
                // fallback to generated confirmation document
            }
        }

        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
        Payment payment = paymentRepo.findByOrder_Id(order.getId()).orElse(null);
        byte[] pdf = invoiceService.generateDocumentPdf(order, items, isConfirmation, qrBillService, payment);

        String prefix = isConfirmation ? "confirmation-" : "invoice-";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + prefix + displayOrderNumber + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
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

    private Path buildConfirmationPdfRelativePath(UUID orderId, String orderNumber) {
        return Path.of("orders", orderId.toString(), "documents", "confirmation-" + orderNumber + ".pdf");
    }
}

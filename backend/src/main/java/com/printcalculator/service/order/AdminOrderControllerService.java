package com.printcalculator.service.order;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.AdminOrderStatusUpdateRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.dto.OrderItemDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.event.OrderShippedEvent;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class AdminOrderControllerService {
    private static final Path QUOTE_STORAGE_ROOT = Paths.get("storage_quotes").toAbsolutePath().normalize();
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
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final PaymentService paymentService;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final ApplicationEventPublisher eventPublisher;

    public AdminOrderControllerService(OrderRepository orderRepo,
                                       OrderItemRepository orderItemRepo,
                                       PaymentRepository paymentRepo,
                                       QuoteLineItemRepository quoteLineItemRepo,
                                       PaymentService paymentService,
                                       StorageService storageService,
                                       InvoicePdfRenderingService invoiceService,
                                       QrBillService qrBillService,
                                       ApplicationEventPublisher eventPublisher) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.paymentService = paymentService;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.eventPublisher = eventPublisher;
    }

    public List<OrderDto> listOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderDto)
                .toList();
    }

    public OrderDto getOrder(UUID orderId) {
        return toOrderDto(getOrderOrThrow(orderId));
    }

    @Transactional
    public OrderDto updatePaymentMethod(UUID orderId, Map<String, String> payload) {
        getOrderOrThrow(orderId);
        String method = payload != null ? payload.get("method") : null;
        if (method == null || method.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Payment method is required");
        }
        paymentService.updatePaymentMethod(orderId, method);
        return toOrderDto(getOrderOrThrow(orderId));
    }

    @Transactional
    public OrderDto updateOrderStatus(UUID orderId, AdminOrderStatusUpdateRequest payload) {
        if (payload == null || payload.getStatus() == null || payload.getStatus().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Status is required");
        }

        Order order = getOrderOrThrow(orderId);
        String normalizedStatus = payload.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ORDER_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid order status. Allowed values: " + String.join(", ", ALLOWED_ORDER_STATUSES)
            );
        }
        String previousStatus = order.getStatus();
        order.setStatus(normalizedStatus);
        Order savedOrder = orderRepo.save(order);

        if (!"SHIPPED".equals(previousStatus) && "SHIPPED".equals(normalizedStatus)) {
            eventPublisher.publishEvent(new OrderShippedEvent(this, savedOrder));
        }

        return toOrderDto(savedOrder);
    }

    public ResponseEntity<Resource> downloadOrderItemFile(UUID orderId, UUID orderItemId) {
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
            Resource resource = loadOrderItemResourceWithRecovery(item, safeRelativePath);
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
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(NOT_FOUND, "File not available");
        }
    }

    public ResponseEntity<byte[]> downloadOrderConfirmation(UUID orderId) {
        return generateDocument(getOrderOrThrow(orderId), true);
    }

    public ResponseEntity<byte[]> downloadOrderInvoice(UUID orderId) {
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

        paymentRepo.findByOrder_Id(order.getId()).ifPresent(payment -> {
            dto.setPaymentStatus(payment.getStatus());
            dto.setPaymentMethod(payment.getMethod());
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
        dto.setIsCadOrder(order.getIsCadOrder());
        dto.setSourceRequestId(order.getSourceRequestId());
        dto.setCadHours(order.getCadHours());
        dto.setCadHourlyRateChf(order.getCadHourlyRateChf());
        dto.setCadTotalChf(order.getCadTotalChf());
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

        List<OrderItemDto> itemDtos = items.stream().map(item -> {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setId(item.getId());
            itemDto.setOriginalFilename(item.getOriginalFilename());
            itemDto.setMaterialCode(item.getMaterialCode());
            itemDto.setColorCode(item.getColorCode());
            if (item.getFilamentVariant() != null) {
                itemDto.setFilamentVariantId(item.getFilamentVariant().getId());
                itemDto.setFilamentVariantDisplayName(item.getFilamentVariant().getVariantDisplayName());
                itemDto.setFilamentColorName(item.getFilamentVariant().getColorName());
                itemDto.setFilamentColorHex(item.getFilamentVariant().getColorHex());
            }
            itemDto.setQuality(item.getQuality());
            itemDto.setNozzleDiameterMm(item.getNozzleDiameterMm());
            itemDto.setLayerHeightMm(item.getLayerHeightMm());
            itemDto.setInfillPercent(item.getInfillPercent());
            itemDto.setInfillPattern(item.getInfillPattern());
            itemDto.setSupportsEnabled(item.getSupportsEnabled());
            itemDto.setQuantity(item.getQuantity());
            itemDto.setPrintTimeSeconds(item.getPrintTimeSeconds());
            itemDto.setMaterialGrams(item.getMaterialGrams());
            itemDto.setUnitPriceChf(item.getUnitPriceChf());
            itemDto.setLineTotalChf(item.getLineTotalChf());
            return itemDto;
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

    private Resource loadOrderItemResourceWithRecovery(OrderItem item, Path safeRelativePath) {
        try {
            return storageService.loadAsResource(safeRelativePath);
        } catch (Exception primaryFailure) {
            Path sourceQuotePath = resolveFallbackQuoteItemPath(item);
            if (sourceQuotePath == null) {
                throw new ResponseStatusException(NOT_FOUND, "File not available");
            }
            try {
                storageService.store(sourceQuotePath, safeRelativePath);
                return storageService.loadAsResource(safeRelativePath);
            } catch (Exception copyFailure) {
                try {
                    Resource quoteResource = new UrlResource(sourceQuotePath.toUri());
                    if (quoteResource.exists() || quoteResource.isReadable()) {
                        return quoteResource;
                    }
                } catch (Exception ignored) {
                    // fall through to 404
                }
                throw new ResponseStatusException(NOT_FOUND, "File not available");
            }
        }
    }

    private Path resolveFallbackQuoteItemPath(OrderItem orderItem) {
        Order order = orderItem.getOrder();
        QuoteSession sourceSession = order != null ? order.getSourceQuoteSession() : null;
        UUID sourceSessionId = sourceSession != null ? sourceSession.getId() : null;
        if (sourceSessionId == null) {
            return null;
        }

        String targetFilename = normalizeFilename(orderItem.getOriginalFilename());
        if (targetFilename == null) {
            return null;
        }

        return quoteLineItemRepo.findByQuoteSessionId(sourceSessionId).stream()
                .filter(quoteItem -> targetFilename.equals(normalizeFilename(quoteItem.getOriginalFilename())))
                .sorted(Comparator.comparingInt((QuoteLineItem quoteItem) -> scoreQuoteMatch(orderItem, quoteItem)).reversed())
                .map(quoteItem -> resolveStoredQuotePath(quoteItem.getStoredPath(), sourceSessionId))
                .filter(path -> path != null && Files.exists(path))
                .findFirst()
                .orElse(null);
    }

    private int scoreQuoteMatch(OrderItem orderItem, QuoteLineItem quoteItem) {
        int score = 0;
        if (orderItem.getQuantity() != null && orderItem.getQuantity().equals(quoteItem.getQuantity())) {
            score += 4;
        }
        if (orderItem.getPrintTimeSeconds() != null && orderItem.getPrintTimeSeconds().equals(quoteItem.getPrintTimeSeconds())) {
            score += 3;
        }
        if (orderItem.getMaterialCode() != null
                && quoteItem.getMaterialCode() != null
                && orderItem.getMaterialCode().equalsIgnoreCase(quoteItem.getMaterialCode())) {
            score += 3;
        }
        if (orderItem.getMaterialGrams() != null
                && quoteItem.getMaterialGrams() != null
                && orderItem.getMaterialGrams().compareTo(quoteItem.getMaterialGrams()) == 0) {
            score += 2;
        }
        return score;
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        return filename.trim();
    }

    private Path resolveStoredQuotePath(String storedPath, UUID expectedSessionId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        try {
            Path raw = Path.of(storedPath).normalize();
            Path resolved = raw.isAbsolute() ? raw : QUOTE_STORAGE_ROOT.resolve(raw).normalize();
            Path expectedSessionRoot = QUOTE_STORAGE_ROOT.resolve(expectedSessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private Path buildConfirmationPdfRelativePath(UUID orderId, String orderNumber) {
        return Path.of("orders", orderId.toString(), "documents", "confirmation-" + orderNumber + ".pdf");
    }
}

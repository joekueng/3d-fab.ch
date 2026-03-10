package com.printcalculator.service.order;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.dto.OrderItemDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.payment.TwintPaymentService;
import com.printcalculator.service.storage.StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderControllerService {
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Set<String> PERSONAL_DATA_REDACTED_STATUSES = Set.of(
            "IN_PRODUCTION",
            "SHIPPED",
            "COMPLETED"
    );

    private final OrderService orderService;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final TwintPaymentService twintPaymentService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepo;

    public OrderControllerService(OrderService orderService,
                                  OrderRepository orderRepo,
                                  OrderItemRepository orderItemRepo,
                                  StorageService storageService,
                                  InvoicePdfRenderingService invoiceService,
                                  QrBillService qrBillService,
                                  TwintPaymentService twintPaymentService,
                                  PaymentService paymentService,
                                  PaymentRepository paymentRepo) {
        this.orderService = orderService;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.twintPaymentService = twintPaymentService;
        this.paymentService = paymentService;
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public OrderDto createOrderFromQuote(UUID quoteSessionId, CreateOrderRequest request) {
        Order order = orderService.createOrderFromQuote(quoteSessionId, request);
        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
        return convertToDto(order, items);
    }

    @Transactional
    public boolean uploadOrderItemFile(UUID orderId, UUID orderItemId, MultipartFile file) throws IOException {
        OrderItem item = orderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new RuntimeException("OrderItem not found"));

        if (!item.getOrder().getId().equals(orderId)) {
            return false;
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
                return false;
            }
        }

        storageService.store(file, destinationRelativePath);
        item.setFileSizeBytes(file.getSize());
        item.setMimeType(file.getContentType());
        orderItemRepo.save(item);

        return true;
    }

    public Optional<OrderDto> getOrder(UUID orderId) {
        return orderRepo.findById(orderId)
                .map(order -> {
                    List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
                    return convertToDto(order, items);
                });
    }

    @Transactional
    public Optional<OrderDto> reportPayment(UUID orderId, String method) {
        paymentService.reportPayment(orderId, method);
        return getOrder(orderId);
    }

    public ResponseEntity<byte[]> getConfirmation(UUID orderId) {
        return generateDocument(orderId, true);
    }

    public ResponseEntity<Map<String, String>> getTwintPayment(UUID orderId) {
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

    public ResponseEntity<Void> openTwintPayment(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(302)
                .location(URI.create(twintPaymentService.getTwintPaymentUrl(order)))
                .build();
    }

    public ResponseEntity<byte[]> getTwintQr(UUID orderId, int size) {
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

    private String getExtension(String filename) {
        if (filename == null) {
            return "stl";
        }
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
        dto.setSourceType(order.getSourceType() != null ? order.getSourceType() : "CALCULATOR");
        dto.setStatus(order.getStatus());

        paymentRepo.findByOrder_Id(order.getId()).ifPresent(payment -> {
            dto.setPaymentStatus(payment.getStatus());
            dto.setPaymentMethod(payment.getMethod());
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
        }

        List<OrderItemDto> itemDtos = items.stream().map(item -> {
            OrderItemDto itemDto = new OrderItemDto();
            itemDto.setId(item.getId());
            itemDto.setItemType(item.getItemType() != null ? item.getItemType() : "PRINT_FILE");
            itemDto.setOriginalFilename(item.getOriginalFilename());
            itemDto.setDisplayName(
                    item.getDisplayName() != null && !item.getDisplayName().isBlank()
                            ? item.getDisplayName()
                            : item.getOriginalFilename()
            );
            itemDto.setMaterialCode(item.getMaterialCode());
            itemDto.setColorCode(item.getColorCode());
            if (item.getShopProduct() != null) {
                itemDto.setShopProductId(item.getShopProduct().getId());
            }
            if (item.getShopProductVariant() != null) {
                itemDto.setShopProductVariantId(item.getShopProductVariant().getId());
            }
            itemDto.setShopProductSlug(item.getShopProductSlug());
            itemDto.setShopProductName(item.getShopProductName());
            itemDto.setShopVariantLabel(item.getShopVariantLabel());
            itemDto.setShopVariantColorName(item.getShopVariantColorName());
            itemDto.setShopVariantColorHex(item.getShopVariantColorHex());
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

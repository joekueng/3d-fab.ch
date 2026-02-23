package com.printcalculator.service;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.entity.*;
import com.printcalculator.repository.CustomerRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.event.OrderCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final QuoteLineItemRepository quoteLineItemRepo;
    private final CustomerRepository customerRepo;
    private final StorageService storageService;
    private final InvoicePdfRenderingService invoiceService;
    private final QrBillService qrBillService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepo,
                        OrderItemRepository orderItemRepo,
                        QuoteSessionRepository quoteSessionRepo,
                        QuoteLineItemRepository quoteLineItemRepo,
                        CustomerRepository customerRepo,
                        StorageService storageService,
                        InvoicePdfRenderingService invoiceService,
                        QrBillService qrBillService,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.quoteLineItemRepo = quoteLineItemRepo;
        this.customerRepo = customerRepo;
        this.storageService = storageService;
        this.invoiceService = invoiceService;
        this.qrBillService = qrBillService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order createOrderFromQuote(UUID quoteSessionId, CreateOrderRequest request) {
        QuoteSession session = quoteSessionRepo.findById(quoteSessionId)
                .orElseThrow(() -> new RuntimeException("Quote Session not found"));

        if (session.getConvertedOrderId() != null) {
            throw new IllegalStateException("Quote session already converted to order");
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

        List<OrderItem> savedItems = new ArrayList<>();

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

            oItem = orderItemRepo.save(oItem);
            savedItems.add(oItem);
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

        // Generate Invoice and QR Bill
        generateAndSaveDocuments(order, savedItems);
        
        Order savedOrder = orderRepo.save(order);
        
        eventPublisher.publishEvent(new OrderCreatedEvent(this, savedOrder));

        return savedOrder;
    }
    
    private void generateAndSaveDocuments(Order order, List<OrderItem> items) {
        try {
            // 1. Generate QR Bill
            byte[] qrBillSvgBytes = qrBillService.generateQrBillSvg(order);
            String qrBillSvg = new String(qrBillSvgBytes, StandardCharsets.UTF_8);

            // Strip XML declaration and DOCTYPE if present, as they validity break the embedding HTML page
            if (qrBillSvg.contains("<?xml")) {
                int svgStartIndex = qrBillSvg.indexOf("<svg");
                if (svgStartIndex != -1) {
                    qrBillSvg = qrBillSvg.substring(svgStartIndex);
                }
            }
            
            // Save QR Bill SVG
            String qrRelativePath = "orders/" + order.getId() + "/documents/qr-bill.svg";
            saveFileBytes(qrBillSvgBytes, qrRelativePath);

            // 2. Prepare Invoice Variables
            Map<String, Object> vars = new HashMap<>();
            vars.put("sellerDisplayName", "3D Fab Switzerland");
            vars.put("sellerAddressLine1", "Sede Ticino, Svizzera");
            vars.put("sellerAddressLine2", "Sede Bienne, Svizzera");
            vars.put("sellerEmail", "info@3dfab.ch");

            vars.put("invoiceNumber", "INV-" + getDisplayOrderNumber(order).toUpperCase());
            vars.put("invoiceDate", order.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
            vars.put("dueDate", order.getCreatedAt().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE));

            String buyerName = "BUSINESS".equals(order.getBillingCustomerType())
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
            vars.put("paymentTermsText", "Appena riceviamo il pagamento l'ordine entrerà nella coda di stampa. Grazie per la fiducia");

            // 3. Generate PDF
            byte[] pdfBytes = invoiceService.generateInvoicePdfBytesFromTemplate(vars, qrBillSvg);
            
            // Save PDF
            String pdfRelativePath = "orders/" + order.getId() + "/documents/invoice-" + order.getId() + ".pdf";
            saveFileBytes(pdfBytes, pdfRelativePath);

        } catch (Exception e) {
            e.printStackTrace(); 
            // Don't fail the order if document generation fails, but log it
            // TODO: Better error handling
        }
    }

    private void saveFileBytes(byte[] content, String relativePath) {
        // Since StorageService takes paths, we might need to write to temp first or check if it supports bytes/streams
        // Simulating via temp file for now as StorageService.store takes a Path
        try {
            Path tempFile = Files.createTempFile("print-calc-upload", ".tmp");
            Files.write(tempFile, content);
            storageService.store(tempFile, Paths.get(relativePath));
            Files.delete(tempFile);
        } catch (IOException e) {
             throw new RuntimeException("Failed to save file " + relativePath, e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "stl";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1);
        }
        return "stl";
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }
}

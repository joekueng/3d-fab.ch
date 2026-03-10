package com.printcalculator.service.payment;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;

@Service
public class InvoicePdfRenderingService {

    private final TemplateEngine thymeleafTemplateEngine;

    public InvoicePdfRenderingService(TemplateEngine thymeleafTemplateEngine) {
        this.thymeleafTemplateEngine = thymeleafTemplateEngine;
    }

    public byte[] generateInvoicePdfBytesFromTemplate(Map<String, Object> invoiceTemplateVariables, String qrBillSvg) {
        try {
            Context thymeleafContextWithInvoiceData = new Context(Locale.ITALY);
            thymeleafContextWithInvoiceData.setVariables(invoiceTemplateVariables);
            thymeleafContextWithInvoiceData.setVariable("qrBillSvg", qrBillSvg);

            String renderedInvoiceHtml = thymeleafTemplateEngine.process("invoice", thymeleafContextWithInvoiceData);

            String classpathBaseUrlForHtmlResources = new ClassPathResource("templates/").getURL().toExternalForm();

            ByteArrayOutputStream generatedPdfByteArrayOutputStream = new ByteArrayOutputStream();

            PdfRendererBuilder openHtmlToPdfRendererBuilder = new PdfRendererBuilder();
            openHtmlToPdfRendererBuilder.useFastMode();
            openHtmlToPdfRendererBuilder.useSVGDrawer(new BatikSVGDrawer());
            openHtmlToPdfRendererBuilder.withHtmlContent(renderedInvoiceHtml, classpathBaseUrlForHtmlResources);
            openHtmlToPdfRendererBuilder.toStream(generatedPdfByteArrayOutputStream);
            openHtmlToPdfRendererBuilder.run();

            return generatedPdfByteArrayOutputStream.toByteArray();
        } catch (Exception pdfGenerationException) {
            throw new IllegalStateException("PDF invoice generation failed", pdfGenerationException);
        }
    }

    public byte[] generateDocumentPdf(Order order, List<OrderItem> items, boolean isConfirmation, QrBillService qrBillService, Payment payment) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("isConfirmation", isConfirmation);
        vars.put("sellerDisplayName", "3D Fab Küng Caletti");
        vars.put("sellerAddressLine1", "Joe Küng e Matteo Caletti");
        vars.put("sellerAddressLine2", "Sede Bienne, Svizzera");
        vars.put("sellerEmail", "info@3dfab.ch");

        String displayOrderNumber = order.getOrderNumber() != null && !order.getOrderNumber().isBlank() 
            ? order.getOrderNumber() 
            : order.getId().toString();

        vars.put("invoiceNumber", "INV-" + displayOrderNumber.toUpperCase());
        vars.put("invoiceDate", order.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        vars.put("dueDate", order.getCreatedAt().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE));

        String buyerName = order.getBillingCustomerType().equals("BUSINESS") 
            ? order.getBillingCompanyName() 
            : order.getBillingFirstName() + " " + order.getBillingLastName();
        vars.put("buyerDisplayName", buyerName);
        vars.put("buyerAddressLine1", order.getBillingAddressLine1());
        vars.put("buyerAddressLine2", order.getBillingZip() + " " + order.getBillingCity() + ", " + order.getBillingCountryCode());

        // Setup Shipping Info
        if (order.getShippingAddressLine1() != null && !order.getShippingAddressLine1().isBlank()) {
            String shippingName = order.getShippingCompanyName() != null && !order.getShippingCompanyName().isBlank()
                ? order.getShippingCompanyName()
                : order.getShippingFirstName() + " " + order.getShippingLastName();
            vars.put("shippingDisplayName", shippingName);
            vars.put("shippingAddressLine1", order.getShippingAddressLine1());
            vars.put("shippingAddressLine2", order.getShippingZip() + " " + order.getShippingCity() + ", " + order.getShippingCountryCode());
        }

        List<Map<String, Object>> invoiceLineItems = items.stream()
                .map(this::toInvoiceLineItem)
                .collect(Collectors.toList());

        if (order.getCadTotalChf() != null && order.getCadTotalChf().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cadHours = order.getCadHours() != null ? order.getCadHours() : BigDecimal.ZERO;
            BigDecimal cadHourlyRate = order.getCadHourlyRateChf() != null ? order.getCadHourlyRateChf() : BigDecimal.ZERO;
            Map<String, Object> cadLine = new HashMap<>();
            cadLine.put("description", "Servizio CAD (" + formatCadHours(cadHours) + "h)");
            cadLine.put("quantity", 1);
            cadLine.put("unitPriceFormatted", String.format("CHF %.2f", cadHourlyRate));
            cadLine.put("lineTotalFormatted", String.format("CHF %.2f", order.getCadTotalChf()));
            invoiceLineItems.add(cadLine);
        }

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
        vars.put("paymentTermsText", isConfirmation ? "Pagamento entro 7 giorni via Bonifico o TWINT. Grazie." : "Pagato. Grazie per l'acquisto.");
        
        String paymentMethodText = "QR / Bonifico oppure TWINT";
        if (payment != null && payment.getMethod() != null) {
            paymentMethodText = switch (payment.getMethod().toUpperCase()) {
                case "TWINT" -> "TWINT";
                case "BANK_TRANSFER", "BONIFICO" -> "Bonifico Bancario";
                case "QR_BILL", "QR" -> "QR Bill";
                case "CASH" -> "Contanti";
                default -> payment.getMethod();
            };
        }
        vars.put("paymentMethodText", paymentMethodText);

        String qrBillSvg = null;
        if (isConfirmation) {
            qrBillSvg = new String(qrBillService.generateQrBillSvg(order), java.nio.charset.StandardCharsets.UTF_8);
            
            if (qrBillSvg.contains("<?xml")) {
                int svgStartIndex = qrBillSvg.indexOf("<svg");
                if (svgStartIndex != -1) {
                    qrBillSvg = qrBillSvg.substring(svgStartIndex);
                }
            }
        }
        
        return generateInvoicePdfBytesFromTemplate(vars, qrBillSvg);
    }

    private String formatCadHours(BigDecimal hours) {
        return hours.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> toInvoiceLineItem(OrderItem item) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", buildLineDescription(item));
        line.put("quantity", item.getQuantity());
        line.put("unitPriceFormatted", String.format("CHF %.2f", item.getUnitPriceChf()));
        line.put("lineTotalFormatted", String.format("CHF %.2f", item.getLineTotalChf()));
        return line;
    }

    private String buildLineDescription(OrderItem item) {
        if (item == null) {
            return "Articolo";
        }

        if ("SHOP_PRODUCT".equalsIgnoreCase(item.getItemType())) {
            String productName = firstNonBlank(
                    item.getDisplayName(),
                    item.getShopProductName(),
                    item.getOriginalFilename(),
                    "Prodotto shop"
            );
            String variantLabel = firstNonBlank(item.getShopVariantLabel(), item.getShopVariantColorName(), null);
            return variantLabel != null ? productName + " - " + variantLabel : productName;
        }

        String fileName = firstNonBlank(item.getDisplayName(), item.getOriginalFilename(), "File 3D");
        return "Stampa 3D: " + fileName;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoicePdfRenderingServiceTest {

    @Test
    void generateDocumentPdf_shouldDescribeShopItemsWithProductAndVariant() {
        CapturingInvoicePdfRenderingService service = new CapturingInvoicePdfRenderingService();
        QrBillService qrBillService = mock(QrBillService.class);
        when(qrBillService.generateQrBillSvg(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenReturn("<svg/>".getBytes(StandardCharsets.UTF_8));

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedAt(OffsetDateTime.parse("2026-03-10T10:15:30+01:00"));
        order.setBillingCustomerType("PRIVATE");
        order.setBillingFirstName("Joe");
        order.setBillingLastName("Buyer");
        order.setBillingAddressLine1("Via Test 1");
        order.setBillingZip("6900");
        order.setBillingCity("Lugano");
        order.setBillingCountryCode("CH");
        order.setSetupCostChf(BigDecimal.ZERO);
        order.setShippingCostChf(new BigDecimal("2.00"));
        order.setSubtotalChf(new BigDecimal("36.80"));
        order.setTotalChf(new BigDecimal("38.80"));
        order.setCadTotalChf(BigDecimal.ZERO);

        OrderItem shopItem = new OrderItem();
        shopItem.setItemType("SHOP_PRODUCT");
        shopItem.setDisplayName("Desk Cable Clip");
        shopItem.setOriginalFilename("desk-cable-clip.stl");
        shopItem.setShopProductName("Desk Cable Clip");
        shopItem.setShopVariantLabel("Coral Red");
        shopItem.setQuantity(2);
        shopItem.setUnitPriceChf(new BigDecimal("14.90"));
        shopItem.setLineTotalChf(new BigDecimal("29.80"));

        OrderItem printItem = new OrderItem();
        printItem.setItemType("PRINT_FILE");
        printItem.setDisplayName("gear-cover.stl");
        printItem.setOriginalFilename("gear-cover.stl");
        printItem.setQuantity(1);
        printItem.setUnitPriceChf(new BigDecimal("7.00"));
        printItem.setLineTotalChf(new BigDecimal("7.00"));

        byte[] pdf = service.generateDocumentPdf(order, List.of(shopItem, printItem), true, qrBillService, null);

        assertNotNull(pdf);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceLineItems = (List<Map<String, Object>>) service.capturedVariables.get("invoiceLineItems");
        assertEquals("Desk Cable Clip - Coral Red", invoiceLineItems.getFirst().get("description"));
        assertEquals("Stampa 3D: gear-cover.stl", invoiceLineItems.get(1).get("description"));
    }

    private static class CapturingInvoicePdfRenderingService extends InvoicePdfRenderingService {
        private Map<String, Object> capturedVariables;

        private CapturingInvoicePdfRenderingService() {
            super(mock(TemplateEngine.class));
        }

        @Override
        public byte[] generateInvoicePdfBytesFromTemplate(Map<String, Object> invoiceTemplateVariables, String qrBillSvg) {
            this.capturedVariables = invoiceTemplateVariables;
            return new byte[]{1, 2, 3};
        }
    }
}

package com.printcalculator.service.email;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.Payment;
import com.printcalculator.event.listener.OrderEmailListener;
import com.printcalculator.repository.*;
import com.printcalculator.service.payment.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentEmailRenderingTest {
    @ParameterizedTest
    @CsvSource({"it,in attesa di produzione", "en,awaiting production", "de,wartet auf Produktion", "fr,en attente de production"})
    void rendersActualLocalizedContextWithPaidInvoice(String language, String expectedStatus) throws Exception {
        var sender = mock(EmailNotificationService.class);
        var invoice = mock(InvoicePdfRenderingService.class);
        var items = mock(OrderItemRepository.class);
        var bill = mock(QrBillService.class);
        var audit = mock(EmailAuditService.class);
        var listener = new OrderEmailListener(sender, invoice, mock(OrderRepository.class), items,
                mock(PaymentRepository.class), bill, audit);
        ReflectionTestUtils.setField(listener, "frontendBaseUrl", "https://example.test");
        Order order = new Order(); order.setId(UUID.fromString("abcdef12-1234-1234-1234-123456789abc"));
        order.setPreferredLanguage(language); order.setBillingFirstName("Cliente"); order.setBillingLastName("di prova");
        order.setCustomerEmail("fixture@example.test"); order.setCreatedAt(OffsetDateTime.now());
        order.setCurrency("CHF"); order.setTotalChf(new BigDecimal("23.90")); order.setStatus("PAID");
        Payment payment = new Payment(); payment.setStatus("RECEIVED");
        byte[] pdf = "fixture-pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(invoice.generateDocumentPdf(eq(order), anyList(), eq(false), eq(bill), eq(payment))).thenReturn(pdf);
        when(sender.sendEmailWithAttachment(eq("fixture@example.test"), anyString(), eq("payment-confirmed"), anyMap(), anyString(), eq(pdf)))
                .thenAnswer(call -> {
                    Map<String, Object> data = call.getArgument(3);
                    var resolver = new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/");
                    resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
                    var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
                    Context context = new Context(); context.setVariables(data);
                    context.setVariable("logoUrl", "https://example.test/logo.svg");
                    String html = engine.process("email/payment-confirmed", context);
                    var dom = Jsoup.parse(html);
                    assertTrue(dom.select(".status-box").text().contains(expectedStatus));
                    assertEquals("https://example.test/" + language + "/co/" + order.getId(), dom.select(".content a").attr("href"));
                    assertEquals(1, dom.select(".container > .header").size());
                    Path target = Path.of("build", "payment-email-preview", language + ".html");
                    Files.createDirectories(target.getParent()); Files.writeString(target, html);
                    return EmailSendResult.sent(OffsetDateTime.now(), OffsetDateTime.now());
                });
        listener.sendPaidInvoiceEmail(order, payment, EmailAuditService.ORIGIN_SYSTEM, null);
        verify(sender).sendEmailWithAttachment(anyString(), anyString(), eq("payment-confirmed"), anyMap(), anyString(), eq(pdf));
        clearInvocations(sender);
        when(invoice.generateDocumentPdf(eq(order), anyList(), eq(false), eq(bill), eq(payment)))
                .thenThrow(new IllegalStateException("Fixture invoice failure"));
        listener.sendPaidInvoiceEmail(order, payment, EmailAuditService.ORIGIN_SYSTEM, null);
        verifyNoInteractions(sender);
        verify(audit).recordOrderEmail(eq(order), eq(EmailAuditService.EVENT_PAYMENT_CONFIRMED_CUSTOMER),
                eq(EmailAuditService.ORIGIN_SYSTEM), eq("fixture@example.test"), anyString(), eq("payment-confirmed"),
                anyString(), argThat(result -> EmailSendResult.STATUS_FAILED.equals(result.status())), isNull());
    }
}

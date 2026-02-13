package com.printcalculator.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Service
public class InvoicePdfRenderingService {

    private final TemplateEngine thymeleafTemplateEngine;

    public InvoicePdfRenderingService(TemplateEngine thymeleafTemplateEngine) {
        this.thymeleafTemplateEngine = thymeleafTemplateEngine;
    }

    public byte[] generateInvoicePdfBytesFromTemplate(Map<String, Object> invoiceTemplateVariables) {
        try {
            Context thymeleafContextWithInvoiceData = new Context(Locale.ITALY);
            thymeleafContextWithInvoiceData.setVariables(invoiceTemplateVariables);

            String renderedInvoiceHtml = thymeleafTemplateEngine.process("invoice", thymeleafContextWithInvoiceData);

            String classpathBaseUrlForHtmlResources = new ClassPathResource("templates/").getURL().toExternalForm();

            ByteArrayOutputStream generatedPdfByteArrayOutputStream = new ByteArrayOutputStream();

            PdfRendererBuilder openHtmlToPdfRendererBuilder = new PdfRendererBuilder();
            openHtmlToPdfRendererBuilder.useFastMode();
            openHtmlToPdfRendererBuilder.withHtmlContent(renderedInvoiceHtml, classpathBaseUrlForHtmlResources);
            openHtmlToPdfRendererBuilder.toStream(generatedPdfByteArrayOutputStream);
            openHtmlToPdfRendererBuilder.run();

            return generatedPdfByteArrayOutputStream.toByteArray();
        } catch (Exception pdfGenerationException) {
            throw new IllegalStateException("PDF invoice generation failed", pdfGenerationException);
        }
    }
}

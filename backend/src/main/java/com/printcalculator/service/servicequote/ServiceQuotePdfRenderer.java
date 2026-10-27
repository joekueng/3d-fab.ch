package com.printcalculator.service.servicequote;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.printcalculator.entity.ServiceQuoteRevision;
import com.printcalculator.entity.ServiceQuoteAcceptance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class ServiceQuotePdfRenderer {
    private final TemplateEngine engine;
    public ServiceQuotePdfRenderer(TemplateEngine engine) { this.engine=engine; }
    public byte[] render(ServiceQuoteRevision revision, ServiceQuoteAcceptance acceptance) {
        try {
            var c=revision.getContent();
            var ctx=new Context(Locale.forLanguageTag(c.language()));
            ctx.setVariable("language",c.language()); ctx.setVariable("labels",ServiceQuoteCopy.labels(c.language()));
            ctx.setVariable("revision",revision);ctx.setVariable("content",c);ctx.setVariable("acceptance",acceptance);
            ctx.setVariable("variation",ServiceQuoteCopy.variation(revision));
            var dates=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").withZone(ZoneId.of("Europe/Zurich"));
            ctx.setVariable("validUntil",dates.format(c.validUntil()));
            ctx.setVariable("location",com.printcalculator.service.payment.InvoiceLanguage.resolve(c.language()).text("location"));
            ctx.setVariable("acceptedAt",acceptance==null?null:dates.format(acceptance.getAcceptedAt()));
            var output=new ByteArrayOutputStream();
            var builder=new PdfRendererBuilder();builder.useFastMode();builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(engine.process("service-quote",ctx),new ClassPathResource("templates/").getURL().toExternalForm());
            builder.toStream(output);builder.run();return output.toByteArray();
        } catch(Exception e) { throw new IllegalStateException("Quote PDF rendering failed",e); }
    }
}

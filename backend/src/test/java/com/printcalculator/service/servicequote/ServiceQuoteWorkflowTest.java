package com.printcalculator.service.servicequote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.ServiceQuoteContent;
import com.printcalculator.dto.ServiceQuoteDtos.*;
import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.email.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.datasource.url=jdbc:h2:mem:service-quotes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    "app.frontend.base-url=https://example.test", "app.mail.admin.address=admin@example.test",
    "storage.service-quotes-root=${java.io.tmpdir}/print-calculator-quote-tests", "logging.level.org.hibernate.SQL=OFF"
},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({ServiceQuoteService.class,ServiceQuoteDocumentService.class,ServiceQuotePdfRenderer.class,EmailAuditService.class,ServiceQuoteWorkflowTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ServiceQuoteWorkflowTest {
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean TemplateEngine templateEngine(){var resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setCharacterEncoding("UTF-8");var engine=new org.thymeleaf.spring6.SpringTemplateEngine();engine.setTemplateResolver(resolver);return engine;}
        @Bean org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer locks(){return p->p.put("hibernate.session_factory.statement_inspector",(org.hibernate.resource.jdbc.spi.StatementInspector) sql->sql.replace("for no key update","for update").replace("cast(? as jsonb)", "? format json"));}
    }
    @Autowired ServiceQuoteService service;
    @Autowired ServiceQuoteDocumentService documentService;
    @Autowired ServiceQuoteRepository quotes;
    @Autowired ServiceQuoteRevisionRepository revisions;
    @Autowired ServiceQuotePublicLinkRepository links;
    @Autowired ServiceQuoteAcceptanceRepository acceptances;
    @Autowired ServiceQuoteDocumentRepository documents;
    @Autowired EmailLogRepository logs;
    @Autowired ObjectMapper mapper;
    @MockitoBean EmailNotificationService mail;

    private record Published(Admin quote,Revision revision,Resume credential) {}
    private Published published(String language) {
        var q=service.create(new Draft(content(language),"Client","Project",null,0L));q=service.publish(q.id(),q.version());
        var r=q.revisions().getFirst();return new Published(q,r,service.resume(r.publicLinkId()));
    }
    static ServiceQuoteContent content(String lang){return new ServiceQuoteContent("CAD prototype", "One design service. <script>escape me</script>",ServiceQuoteContent.PricingMode.HOURLY_ESTIMATE,
        new BigDecimal("10"),new BigDecimal("80"),null,ServiceQuoteContent.VariationType.PERCENTAGE,new BigDecimal("15"),OffsetDateTime.now().plusDays(30),lang,null);}
    static Accept request(String version){return new Accept(new com.printcalculator.dto.ServiceQuoteDtos.Customer("client@example.test","PRIVATE","Ada","Example",null,null,"Example 1","6900","Lugano","CH",null),UUID.randomUUID(),"a".repeat(64),version,true,true,true);}
    private Confirmation accept(Published p,Accept request){return service.accept(p.quote().id(),p.revision().offer().revisionId(),p.credential().token(),request,"203.0.113.15");}

    @Test void legacyRecipientDoesNotConstrainCustomerIdentity() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var json = mapper.writeValueAsString(content("it"));
        var legacy = mapper.readValue(json.substring(0,json.length()-1)+",\"recipientEmail\":\"old@example.test\"}", ServiceQuoteContent.class);
        assertNull(legacy.recipientEmail());
        assertFalse(mapper.writeValueAsString(legacy).contains("recipientEmail"));
        var q=service.create(new Draft(legacy,"Client","Project",null,0L));
        q=service.publish(q.id(),q.version());
        var revision=q.revisions().getFirst();
        var p=new Published(q,revision,service.resume(revision.publicLinkId()));
        var confirmation=accept(p,request(revision.offer().contentVersion()));
        assertEquals("client@example.test",acceptances.findById(confirmation.acceptanceId()).orElseThrow().getCustomer().email());
    }

    @Test void freezesAmountsAndIdentityAndIdempotentRetriesCannotExtendAccess() {
        var p=published("it");var payload=request(p.revision().offer().contentVersion());var c=accept(p,payload);
        assertEquals(new BigDecimal("800.00"),p.revision().offer().estimatedAmountChf());assertEquals(new BigDecimal("920.00"),p.revision().offer().maximumAmountChf());
        var stored = acceptances.findById(c.acceptanceId()).orElseThrow();
        assertEquals(ServiceQuoteService.hash(payload.downloadToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)), stored.getDownloadTokenHash());
        assertTrue(stored.getDownloadExpiresAt().isAfter(OffsetDateTime.now()), "Stored expiry: " + stored.getDownloadExpiresAt());
        var retry=accept(p,payload);assertEquals(c.acceptanceId(),retry.acceptanceId());assertEquals(c.downloadExpiresAt(),retry.downloadExpiresAt());
        assertEquals("PENDING",service.downloadStatus(c.acceptanceId(),payload.downloadToken()).documentStatus());
        assertThrows(ResponseStatusException.class,()->service.downloadStatus(c.acceptanceId(),"b".repeat(64)));
        assertThrows(ResponseStatusException.class,()->accept(p,request(payload.contentVersion())));
        assertEquals("ACCEPTED",service.resume(p.revision().publicLinkId()).status());assertNull(service.resume(p.revision().publicLinkId()).token());
        var saved=acceptances.findById(c.acceptanceId()).orElseThrow();assertEquals("203.0.113.15",saved.getClientIp());assertEquals("Ada",saved.getCustomer().firstName());
        assertNotEquals(payload.downloadToken(),saved.getDownloadTokenHash());
        var ref=service.resolveAccepted(p.quote().id(),null);assertEquals(c.acceptanceId(),ref.acceptanceId());
        assertEquals(p.revision().offer().revisionId(),ref.revisionId());
    }
    @Test void expiredDownloadCannotBeRenewedByReplayingTheOriginalRequest() {
        var p=published("en");var payload=request(p.revision().offer().contentVersion());var c=accept(p,payload);
        // Audit fields are not updatable via JPA; use SQL only to exercise expiry with an existing record.
        jdbc.update("update service_quote_acceptances set download_expires_at=? where id=?",OffsetDateTime.now().minusMinutes(1),c.acceptanceId());
        assertThrows(ResponseStatusException.class,()->service.downloadStatus(c.acceptanceId(),payload.downloadToken()));
        assertThrows(ResponseStatusException.class,()->accept(p,payload));
        assertNull(service.resume(p.revision().publicLinkId()).token());
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void replacementRevokesOldBrowserContentAndOptimisticVersionProtectsDrafts() {
        var p=published("it");var q=service.saveDraft(p.quote().id(),new Draft(content("en"),"Client","Project",null,p.quote().version()));
        assertThrows(ResponseStatusException.class,()->service.publish(p.quote().id(),p.quote().version()));
        q=service.publish(q.id(),q.version());
        assertThrows(ResponseStatusException.class,()->accept(p,request(p.revision().offer().contentVersion())));
        assertThrows(ResponseStatusException.class,()->service.resume(p.revision().publicLinkId()));
        assertEquals(2,q.revisions().size());assertEquals("SUPERSEDED",q.revisions().get(1).offer().status());
    }
    @Test void simultaneousAcceptancesProduceOnlyOneRecord() throws Exception {
        var p=published("en");var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> task=()->{start.await();try{accept(p,request(p.revision().offer().contentVersion()));return true;}catch(ResponseStatusException e){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertTrue(acceptances.findByRevisionId(p.revision().offer().revisionId()).isPresent());
        }
    }
    @Test void fullDocumentUsesSameBytesForDownloadEmailAndLaterAdminAccess() throws Exception {
        when(mail.sendEmailWithAttachment(anyString(),anyString(),anyString(),anyMap(),anyString(),any())).thenReturn(EmailSendResult.sent(OffsetDateTime.now(),OffsetDateTime.now()));
        when(mail.sendEmail(anyString(),anyString(),anyString(),anyMap())).thenReturn(EmailSendResult.sent(OffsetDateTime.now(),OffsetDateTime.now()));
        var p=published("it");var payload=request(p.revision().offer().contentVersion());var c=accept(p,payload);
        documentService.process(c.acceptanceId());
        byte[] pdf=documentService.download(c.acceptanceId());assertTrue(new String(pdf,0,4).startsWith("%PDF"));
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(mail).sendEmailWithAttachment(eq("client@example.test"),anyString(),eq("service-quote"),anyMap(),contains(p.quote().id().toString()),bytes.capture());
        assertArrayEquals(pdf,bytes.getValue());assertEquals("READY",service.downloadStatus(c.acceptanceId(),payload.downloadToken()).documentStatus());
        assertEquals("SENT",service.downloadStatus(c.acceptanceId(),payload.downloadToken()).emailStatus());
        documentService.resend(c.acceptanceId());assertArrayEquals(pdf,documentService.download(c.acceptanceId()));
        assertNotNull(service.adminAcceptance(p.quote().id(),c.acceptanceId()));
        assertThrows(ResponseStatusException.class,()->service.adminAcceptance(UUID.randomUUID(),c.acceptanceId()));
        // Retain a synthetic artifact for visual QA outside the source tree.
        Files.write(Path.of(System.getProperty("java.io.tmpdir"),"print-calculator-service-quote.pdf"),pdf);
        try(var parsed=org.apache.pdfbox.Loader.loadPDF(pdf)){
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(parsed);
            assertTrue(text.contains("920.00"));assertTrue(text.contains("Ada Example"));assertTrue(text.contains("Accetto il preventivo"));assertFalse(text.contains("203.0.113.15"));
        }
    }
    @Test void smtpFailureLeavesAcceptanceAndPdfAvailable() {
        when(mail.sendEmailWithAttachment(anyString(),anyString(),anyString(),anyMap(),anyString(),any())).thenReturn(EmailSendResult.failed(OffsetDateTime.now(),"simulated"));
        when(mail.sendEmail(anyString(),anyString(),anyString(),anyMap())).thenReturn(EmailSendResult.skipped(OffsetDateTime.now(),"test"));
        var p=published("fr");var payload=request(p.revision().offer().contentVersion());var c=accept(p,payload);documentService.process(c.acceptanceId());
        assertEquals("FAILED",service.downloadStatus(c.acceptanceId(),payload.downloadToken()).emailStatus());assertTrue(documentService.download(c.acceptanceId()).length>100);
        assertTrue(acceptances.findById(c.acceptanceId()).isPresent());
    }
    @Test void newAcceptedRevisionNeverRewritesTheOldAcceptanceAndRequiresExplicitSelection() {
        var p=published("it"); var original=accept(p,request(p.revision().offer().contentVersion()));
        var q=service.saveDraft(p.quote().id(),new Draft(content("de"),"Changed internal label","Project",null,p.quote().version()));
        q=service.publish(q.id(),q.version()); var r=q.revisions().getFirst();
        var second=new Published(q,r,service.resume(r.publicLinkId()));accept(second,request(r.offer().contentVersion()));
        assertThrows(ResponseStatusException.class,()->service.resolveAccepted(p.quote().id(),null));
        assertEquals(original.acceptanceId(),service.resolveAccepted(p.quote().id(),p.revision().offer().revisionId()).acceptanceId());
        assertEquals("it",revisions.findById(p.revision().offer().revisionId()).orElseThrow().getContent().language());
        assertTrue(service.get(p.quote().id()).revisions().stream().allMatch(v->"ACCEPTED".equals(v.offer().status())));
    }
    @Autowired ServiceQuotePdfRenderer renderer;
    @Autowired TemplateEngine templateEngine;
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"it","en","de","fr"})
    void localizedPdfAndSharedEmailLayoutRenderRealTemplates(String language) throws Exception {
        var p=published(language);var r=service.preview(p.quote().id(),p.revision().offer().revisionId());
        byte[] bytes=renderer.render(r,null);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains(ServiceQuoteCopy.labels(language).get("estimate")));
        }
        var ctx=new org.thymeleaf.context.Context();var copy=ServiceQuoteCopy.labels(language);
        ctx.setVariables(ServiceQuoteDocumentService.emailContext(language,copy.get("inviteSubject"),copy.get("inviteIntro"),r.getContent().title()));
        ctx.setVariable("logoUrl","https://example.test/logo.svg");ctx.setVariable("actionUrl",p.revision().publicUrl());ctx.setVariable("actionText",copy.get("action"));
        var html=org.jsoup.Jsoup.parse(templateEngine.process("email/service-quote",ctx));
        assertEquals(language,html.selectFirst("html").attr("lang"));assertEquals(p.revision().publicUrl(),html.selectFirst("a").attr("href"));
        assertNotNull(html.selectFirst("img[src=https://example.test/logo.svg]"));
    }
    @Test void longDescriptionFlowsAcrossPagesAndPreservesItsEnd() throws Exception {
        var p=published("en");var r=service.preview(p.quote().id(),p.revision().offer().revisionId());var c=r.getContent();
        r.setContent(new ServiceQuoteContent(c.title(),"Detailed prototype scope and technical requirements.\n".repeat(200)+"SCOPE_END",c.pricingMode(),c.estimatedHours(),c.hourlyRateChf(),c.lumpSumChf(),c.variationType(),c.variationValue(),c.validUntil(),c.language(),null));
        byte[] bytes=renderer.render(r,null);
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {assertTrue(pdf.getNumberOfPages()>1);assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("SCOPE_END"));}
        Files.write(Path.of(System.getProperty("java.io.tmpdir"),"print-calculator-service-quote-long.pdf"),bytes);
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired EmailAuditService audit;
    @TempDir Path failedStorage;
    @Test void renderingFailureKeepsAcceptanceAndCanBeRetriedWithoutNewPublicAccess() {
        var p=published("en");var payload=request(p.revision().offer().contentVersion());var confirmation=accept(p,payload);
        var failing=mock(ServiceQuotePdfRenderer.class);when(failing.render(any(),any())).thenThrow(new IllegalStateException("simulated renderer failure"));
        var worker=new ServiceQuoteDocumentService(documents,acceptances,revisions,failing,mail,audit,transactions,failedStorage.toString(),"admin@example.test");
        worker.process(confirmation.acceptanceId());
        assertEquals("FAILED",documents.findByAcceptanceId(confirmation.acceptanceId()).orElseThrow().getStatus());
        assertTrue(acceptances.findById(confirmation.acceptanceId()).isPresent());
        verifyNoInteractions(mail);
        documentService.process(confirmation.acceptanceId());
        assertEquals("READY",documents.findByAcceptanceId(confirmation.acceptanceId()).orElseThrow().getStatus());
        assertNull(service.resume(p.revision().publicLinkId()).token());
    }

}

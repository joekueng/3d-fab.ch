package com.printcalculator.service.servicequote;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import com.printcalculator.service.email.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

/** Durable PDF processing; persisted states recover work missed by the after-commit listener. */
@Service
public class ServiceQuoteDocumentService {
    private final ServiceQuoteDocumentRepository documents;
    private final ServiceQuoteAcceptanceRepository acceptances;
    private final ServiceQuoteRevisionRepository revisions;
    private final ServiceQuotePdfRenderer renderer;
    private final EmailNotificationService mail;
    private final EmailAuditService audit;
    private final TransactionTemplate transactions;
    private final Path root;
    private final String adminEmail;
    public ServiceQuoteDocumentService(ServiceQuoteDocumentRepository documents, ServiceQuoteAcceptanceRepository acceptances,
            ServiceQuoteRevisionRepository revisions, ServiceQuotePdfRenderer renderer, EmailNotificationService mail,
            EmailAuditService audit, PlatformTransactionManager manager,
            @Value("${storage.service-quotes-root:storage_orders/service-quotes}") String root,
            @Value("${app.mail.admin.address}") String adminEmail) {
        this.documents=documents;this.acceptances=acceptances;this.revisions=revisions;this.renderer=renderer;
        this.mail=mail;this.audit=audit;this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.root=Path.of(root).toAbsolutePath().normalize();this.adminEmail=adminEmail;
    }
    public void process(UUID acceptanceId) {
        try {
            transactions.executeWithoutResult(tx->{
                var d=documents.lockByAcceptanceId(acceptanceId).orElseThrow();
                if("READY".equals(d.getStatus()))return;
                var a=acceptances.findById(acceptanceId).orElseThrow();
                var r=revisions.findById(a.getRevisionId()).orElseThrow();
                byte[] bytes=renderer.render(r,a);
                try {
                    Files.createDirectories(root);
                    Path target=root.resolve(d.getId()+".pdf");Path temp=Files.createTempFile(root,"quote-",".tmp");
                    try { Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                    finally {Files.deleteIfExists(temp);}
                    d.setStorageKey(target.getFileName().toString());d.setDocumentHash(ServiceQuoteService.hash(bytes));
                    d.setSizeBytes((long)bytes.length);d.setGeneratedAt(OffsetDateTime.now(java.time.ZoneOffset.UTC));d.setStatus("READY");
                    documents.save(d);
                } catch(IOException e) {throw new IllegalStateException("Quote PDF storage failed",e);}
            });
        } catch(RuntimeException e) {
            transactions.executeWithoutResult(tx->documents.lockByAcceptanceId(acceptanceId).ifPresent(d->{if(!"READY".equals(d.getStatus())){d.setStatus("FAILED");documents.save(d);}}));
            return;
        }
        send(acceptanceId,false,false);send(acceptanceId,true,false);
    }
    @Scheduled(fixedDelayString="${service-quotes.recovery-delay-ms:60000}")
    public void recoverPending() {
        for(var d:documents.findPending(PageRequest.of(0,20)))process(d.getAcceptanceId());
    }
    public void resend(UUID acceptanceId) {
        var document=documents.findByAcceptanceId(acceptanceId).orElseThrow();
        boolean resend=!"PENDING".equals(document.getEmailStatus());
        process(acceptanceId);
        if(resend)send(acceptanceId,false,true);
    }
    private record Sent(UUID quoteId,UUID revisionId,UUID documentId,String event,String recipient,String subject,String filename,EmailSendResult result) {}
    private void send(UUID id,boolean admin,boolean force) {
        Sent sent=transactions.execute(tx->{
            var d=documents.lockByAcceptanceId(id).orElseThrow();
            String current=admin?d.getAdminEmailStatus():d.getEmailStatus();
            if(!"READY".equals(d.getStatus()) || !force && !"PENDING".equals(current))return null;
            var a=acceptances.findById(id).orElseThrow();var r=revisions.findById(a.getRevisionId()).orElseThrow();
            var copy=ServiceQuoteCopy.labels(r.getContent().language());
            String subject=copy.get(admin?"adminSubject":"receiptSubject");String recipient=admin?adminEmail:a.getCustomer().email().trim();
            Map<String,Object> context=emailContext(r.getContent().language(),subject,copy.get(admin?"adminIntro":"receiptIntro"),r.getContent().title());
            EmailSendResult result;
            try { result=admin?mail.sendEmail(recipient,subject,"service-quote",context)
                :mail.sendEmailWithAttachment(recipient,subject,"service-quote",context,filename(r),read(d)); }
            catch(Exception ex) { result=EmailSendResult.failed(OffsetDateTime.now(),"QUOTE_EMAIL_FAILED"); }
            if(result==null)result=EmailSendResult.unknown(OffsetDateTime.now(),"QUOTE_EMAIL_FAILED");
            if(admin)d.setAdminEmailStatus(result.status());else d.setEmailStatus(result.status());
            documents.save(d);
            return new Sent(r.getQuoteId(),r.getId(),d.getId(),admin?"SERVICE_QUOTE_ACCEPTED_ADMIN":"SERVICE_QUOTE_RECEIPT",recipient,subject,admin?null:filename(r),result);
        });
        if(sent!=null)audit.recordServiceQuoteEmail(sent.quoteId(),sent.revisionId(),sent.documentId(),sent.event(),sent.recipient(),sent.subject(),sent.filename(),sent.result());
    }
    public byte[] download(UUID acceptanceId) {
        var d=documents.findByAcceptanceId(acceptanceId).orElseThrow(()->new ResponseStatusException(NOT_FOUND));
        if(!"READY".equals(d.getStatus()))throw new ResponseStatusException(CONFLICT,"QUOTE_PDF_NOT_READY");
        return read(d);
    }
    private byte[] read(ServiceQuoteDocument d) {
        if(d.getStorageKey()==null)throw new ResponseStatusException(CONFLICT,"QUOTE_PDF_NOT_READY");
        Path path=root.resolve(d.getStorageKey()).normalize();
        if(!path.getParent().equals(root))throw new IllegalStateException("Invalid document storage key");
        try {byte[] bytes=Files.readAllBytes(path);
            if(!ServiceQuoteService.constantEquals(d.getDocumentHash(),ServiceQuoteService.hash(bytes)))throw new IOException("Document hash mismatch");
            return bytes;
        }catch(IOException e){throw new ResponseStatusException(INTERNAL_SERVER_ERROR,"QUOTE_PDF_UNAVAILABLE");}
    }
    public static String filename(ServiceQuoteRevision r){return "preventivo-"+r.getQuoteId()+"-r"+r.getRevisionNumber()+"-accettato.pdf";}
    public static Map<String,Object> emailContext(String language,String title,String intro,String quoteTitle) {
        var context=new HashMap<String,Object>();context.put("language",language);context.put("emailTitle",title);
        context.put("introText",intro);context.put("quoteTitle",quoteTitle);context.put("footerText",ServiceQuoteCopy.labels(language).get("footer"));
        context.put("currentYear",java.time.Year.now().getValue());return context;
    }
}

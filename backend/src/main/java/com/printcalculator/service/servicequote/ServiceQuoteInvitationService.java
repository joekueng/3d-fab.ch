package com.printcalculator.service.servicequote;

import com.printcalculator.service.email.*;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ServiceQuoteInvitationService {
    private final ServiceQuoteService quotes;
    private final EmailNotificationService mail;
    private final EmailAuditService audit;
    private final ServiceQuoteRateLimiter limiter;
    public ServiceQuoteInvitationService(ServiceQuoteService quotes,EmailNotificationService mail,EmailAuditService audit,ServiceQuoteRateLimiter limiter){this.quotes=quotes;this.mail=mail;this.audit=audit;this.limiter=limiter;}
    public String send(UUID quoteId,UUID revisionId,String email) {
        limiter.check("email:"+email.trim().toLowerCase(java.util.Locale.ROOT),1);
        var invitation=quotes.invitation(quoteId,revisionId);var copy=ServiceQuoteCopy.labels(invitation.language());
        String subject=copy.get("inviteSubject");
        var context=ServiceQuoteDocumentService.emailContext(invitation.language(),subject,copy.get("inviteIntro"),invitation.title());
        context.put("actionUrl",invitation.url());context.put("actionText",copy.get("action"));
        EmailSendResult result;
        try {result=mail.sendEmail(email.trim(),subject,"service-quote",context);}
        catch(Exception e){result=EmailSendResult.failed(java.time.OffsetDateTime.now(),"QUOTE_EMAIL_FAILED");}
        if(result==null)result=EmailSendResult.unknown(java.time.OffsetDateTime.now(),"QUOTE_EMAIL_FAILED");
        audit.recordServiceQuoteEmail(quoteId,revisionId,null,"SERVICE_QUOTE_INVITATION",email.trim(),subject,null,result);
        return result.status();
    }
}

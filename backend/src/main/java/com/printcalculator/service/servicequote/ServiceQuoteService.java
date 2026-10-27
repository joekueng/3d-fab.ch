package com.printcalculator.service.servicequote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.*;
import com.printcalculator.dto.ServiceQuoteDtos.*;
import com.printcalculator.entity.*;
import com.printcalculator.event.ServiceQuoteAcceptedEvent;
import com.printcalculator.repository.*;
import com.printcalculator.security.IpAddressUtils;
import com.printcalculator.service.LegalConsentService;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.information.OrderInformationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class ServiceQuoteService {
    private final ServiceQuoteRepository quotes;
    private final ServiceQuoteRevisionRepository revisions;
    private final ServiceQuotePublicLinkRepository links;
    private final ServiceQuoteAcceptanceRepository acceptances;
    private final ServiceQuoteDocumentRepository documents;
    private final CustomQuoteRequestRepository requests;
    private final QuoteSessionRepository sessions;
    private final EmailLogRepository emailLogs;
    private final EmailAuditService audit;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final String frontend;

    public ServiceQuoteService(ServiceQuoteRepository quotes, ServiceQuoteRevisionRepository revisions,
            ServiceQuotePublicLinkRepository links, ServiceQuoteAcceptanceRepository acceptances,
            ServiceQuoteDocumentRepository documents, CustomQuoteRequestRepository requests,
            QuoteSessionRepository sessions, EmailLogRepository emailLogs, EmailAuditService audit,
            ObjectMapper mapper, ApplicationEventPublisher events, @Value("${app.frontend.base-url}") String frontend) {
        this.quotes=quotes; this.revisions=revisions; this.links=links; this.acceptances=acceptances;
        this.documents=documents; this.requests=requests; this.sessions=sessions; this.emailLogs=emailLogs;
        this.audit=audit; this.mapper=mapper; this.events=events; this.frontend=frontend.replaceAll("/+$", "");
    }
    public List<Admin> list() { return quotes.findAllByOrderByCreatedAtDesc().stream().map(this::admin).toList(); }
    public Admin get(UUID id) { return admin(quote(id)); }
    public Admin create(Draft request) {
        if (request.version()!=0) throw error(CONFLICT,"QUOTE_VERSION");
        var q=new ServiceQuote(); update(q,request); return admin(quotes.saveAndFlush(q));
    }
    public Admin saveDraft(UUID id, Draft request) {
        var q=lock(id); version(q,request.version()); update(q,request);
        return admin(quotes.saveAndFlush(q));
    }
    private void update(ServiceQuote q, Draft request) {
        ServiceQuoteMath.calculate(request.content());
        if (request.sourceRequestId()!=null && !requests.existsById(request.sourceRequestId())) throw error(BAD_REQUEST,"QUOTE_REQUEST");
        q.setDraft(request.content()); q.setClientName(clean(request.clientName()));
        q.setCollaborationName(clean(request.collaborationName())); q.setSourceRequestId(request.sourceRequestId());
    }
    public Admin publish(UUID id, long expectedVersion) {
        var q=lock(id); version(q,expectedVersion);
        var c=q.getDraft();
        if (c==null || !c.validUntil().isAfter(now())) throw error(BAD_REQUEST,"QUOTE_VALIDITY");
        var totals=ServiceQuoteMath.calculate(c);
        for (var previous:revisions.findByQuoteIdOrderByRevisionNumberDesc(id)) {
            if (acceptances.findByRevisionId(previous.getId()).isEmpty()) {
                previous.setSupersededAt(now());
                links.findByRevisionId(previous.getId()).ifPresent(l->l.setRevokedAt(now()));
            }
        }
        var r=new ServiceQuoteRevision(); r.setQuoteId(id); r.setRevisionNumber(q.getNextRevision());
        r.setContent(c); r.setEstimatedAmountChf(totals.estimate()); r.setMaximumAmountChf(totals.maximum());
        r.setContentVersion("draft-acceptance-v1-"+UUID.randomUUID());
        r.setTermsVersion(LegalConsentService.TERMS_VERSION); r.setPrivacyVersion(LegalConsentService.PRIVACY_VERSION);
        r.setAcceptanceText(ServiceQuoteCopy.declaration(r)); r.setPublishedAt(now()); revisions.saveAndFlush(r);
        var link=new ServiceQuotePublicLink(); link.setId(UUID.randomUUID()); link.setRevisionId(r.getId());
        link.setAccessToken(OrderInformationService.newToken()); link.setCreatedAt(now()); links.save(link);
        q.setNextRevision(q.getNextRevision()+1); q.setDraft(null); quotes.saveAndFlush(q);
        return admin(q);
    }
    public Admin revoke(UUID quoteId, UUID revisionId) {
        lock(quoteId); var r=revision(quoteId,revisionId);
        if (acceptances.findByRevisionId(revisionId).isPresent()) throw error(CONFLICT,"QUOTE_ALREADY_ACCEPTED");
        r.setRevokedAt(now()); links.findByRevisionId(revisionId).ifPresent(l->l.setRevokedAt(now()));
        return admin(quote(quoteId));
    }
    public Resume resume(UUID linkId) {
        var link=links.findById(linkId).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));
        var r=revisions.findById(link.getRevisionId()).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));
        lock(r.getQuoteId());
        if (acceptances.findByRevisionId(r.getId()).isPresent()) return new Resume("ACCEPTED",null,null,null);
        available(r,link);
        return new Resume("PUBLISHED",r.getQuoteId(),r.getId(),link.getAccessToken());
    }
    public Offer offer(UUID quoteId, UUID revisionId, String token) {
        lock(quoteId); var r=revision(quoteId,revisionId); var link=authorize(r,token);
        available(r,link);
        if (acceptances.findByRevisionId(revisionId).isPresent()) throw error(GONE,"QUOTE_ALREADY_ACCEPTED");
        return offerDto(r);
    }
    public Confirmation accept(UUID quoteId, UUID revisionId, String token, Accept request, String clientIp) {
        lock(quoteId); var r=revision(quoteId,revisionId); var link=authorize(r,token);
        String fingerprint=fingerprint(request);
        var existing=acceptances.findByRevisionId(revisionId);
        if (existing.isPresent()) {
            var a=existing.get();
            if (a.getRequestId().equals(request.requestId()) && constantEquals(a.getRequestHash(),fingerprint)
                    && a.getDownloadExpiresAt().isAfter(now())) return confirmation(a);
            throw error(CONFLICT,"QUOTE_ALREADY_ACCEPTED");
        }
        available(r,link);
        if (!r.getContentVersion().equals(request.contentVersion())) throw error(CONFLICT,"QUOTE_VERSION");
        if (!request.acceptQuote() || !request.acceptTerms() || !request.acceptPrivacy()) throw error(BAD_REQUEST,"QUOTE_CONSENT_REQUIRED");
        var a=new ServiceQuoteAcceptance(); a.setRevisionId(revisionId); a.setCustomer(request.customer());
        a.setAcceptedAt(now()); a.setClientIp(IpAddressUtils.normalizeIp(clientIp)); a.setRequestId(request.requestId());
        a.setRequestHash(fingerprint); a.setDownloadTokenHash(hash(request.downloadToken().getBytes(StandardCharsets.UTF_8)));
        a.setDownloadExpiresAt(a.getAcceptedAt().plusMinutes(15)); a.setAcceptQuote(true); a.setAcceptTerms(true); a.setAcceptPrivacy(true);
        acceptances.saveAndFlush(a);
        var document=new ServiceQuoteDocument(); document.setAcceptanceId(a.getId()); documents.saveAndFlush(document);
        events.publishEvent(new ServiceQuoteAcceptedEvent(a.getId()));
        return confirmation(a);
    }
    public Confirmation downloadStatus(UUID acceptanceId, String token) { return confirmation(authorizeDownload(acceptanceId,token)); }
    public ServiceQuoteAcceptance authorizeDownload(UUID acceptanceId,String token) {
        var a=acceptances.findById(acceptanceId).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));
        if (token==null || !a.getDownloadExpiresAt().isAfter(now())
                || !constantEquals(a.getDownloadTokenHash(),hash(token.getBytes(StandardCharsets.UTF_8)))) throw error(FORBIDDEN,"QUOTE_DOWNLOAD_EXPIRED");
        return a;
    }
    public ServiceQuoteAcceptance adminAcceptance(UUID quoteId, UUID acceptanceId) {
        var a=acceptances.findById(acceptanceId).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));
        revision(quoteId,a.getRevisionId()); return a;
    }
    public ServiceQuoteRevision preview(UUID quoteId, UUID revisionId) { return revision(quoteId,revisionId); }
    public ServiceQuoteRevision draftPreview(UUID id) {
        var q=quote(id); var c=q.getDraft(); if(c==null) throw error(CONFLICT,"QUOTE_NO_DRAFT");
        var r=new ServiceQuoteRevision(); r.setQuoteId(id);r.setRevisionNumber(q.getNextRevision());r.setContent(c);
        var totals=ServiceQuoteMath.calculate(c);r.setEstimatedAmountChf(totals.estimate());r.setMaximumAmountChf(totals.maximum());
        r.setTermsVersion(LegalConsentService.TERMS_VERSION);r.setPrivacyVersion(LegalConsentService.PRIVACY_VERSION);
        r.setAcceptanceText(ServiceQuoteCopy.declaration(r));return r;
    }
    public record Invitation(UUID quoteId, UUID revisionId, String language, String title, String url) {}
    public Invitation invitation(UUID quoteId,UUID revisionId) {
        lock(quoteId); var r=revision(quoteId,revisionId);var link=links.findByRevisionId(revisionId).orElseThrow();
        available(r,link); if(acceptances.findByRevisionId(revisionId).isPresent())throw error(CONFLICT,"QUOTE_ALREADY_ACCEPTED");
        return new Invitation(quoteId,revisionId,r.getContent().language(),r.getContent().title(),url(r,link));
    }
    public ServiceQuoteReference resolveAccepted(UUID quoteId, UUID revisionId) {
        lock(quoteId);
        var accepted=revisions.findByQuoteIdOrderByRevisionNumberDesc(quoteId).stream()
            .filter(r->revisionId==null || revisionId.equals(r.getId()))
            .filter(r->acceptances.findByRevisionId(r.getId()).isPresent()).toList();
        if(accepted.size()!=1) throw error(CONFLICT,"QUOTE_SELECT_ACCEPTED_REVISION");
        var r=accepted.getFirst();var a=acceptances.findByRevisionId(r.getId()).orElseThrow();
        return new ServiceQuoteReference(quoteId,r.getId(),a.getId(),r.getRevisionNumber(),r.getContent().title(),
            r.getEstimatedAmountChf(),r.getMaximumAmountChf(),r.getContent().variationType().name(),r.getContent().variationValue());
    }
    private Confirmation confirmation(ServiceQuoteAcceptance a) {
        var d=documents.findByAcceptanceId(a.getId()).orElseThrow();
        return new Confirmation(a.getId(),d.getStatus(),d.getEmailStatus(),a.getDownloadExpiresAt());
    }
    private Admin admin(ServiceQuote q) {
        var history=revisions.findByQuoteIdOrderByRevisionNumberDesc(q.getId()).stream().map(r->{
            var link=links.findByRevisionId(r.getId()).orElse(null);
            var a=acceptances.findByRevisionId(r.getId()).orElse(null);
            Acceptance acceptance=null;
            if(a!=null){var d=documents.findByAcceptanceId(a.getId()).orElseThrow();
                acceptance=new Acceptance(a.getId(),a.getCustomer(),a.getAcceptedAt(),a.getClientIp(),d.getStatus(),d.getEmailStatus(),d.getAdminEmailStatus(),d.getDocumentHash());}
            return new Revision(offerDto(r),link==null?null:link.getId(),link==null?null:url(r,link),acceptance);
        }).toList();
        var jobs=sessions.findByServiceQuoteId(q.getId()).stream()
            .filter(s->!"CAD_DELETED".equals(s.getStatus()))
            .map(s->new LinkedJob(s.getId(),s.getInvoiceName(),s.getConvertedOrderId())).toList();
        return new Admin(q.getId(),q.getVersion(),q.getClientName(),q.getCollaborationName(),q.getSourceRequestId(),
            q.getDraft(),q.getCreatedAt(),history,jobs,emailLogs.findByServiceQuoteIdOrderByAttemptedAtDesc(q.getId()).stream().map(audit::toDto).toList());
    }
    private Offer offerDto(ServiceQuoteRevision r) {
        var c=r.getContent();
        var safe=new ServiceQuoteContent(c.title(),c.description(),c.pricingMode(),c.estimatedHours(),c.hourlyRateChf(),c.lumpSumChf(),c.variationType(),c.variationValue(),c.validUntil(),c.language(),null);
        return new Offer(r.getQuoteId(),r.getId(),r.getRevisionNumber(),status(r),safe,r.getEstimatedAmountChf(),r.getMaximumAmountChf(),r.getAcceptanceText(),r.getContentVersion(),r.getTermsVersion(),r.getPrivacyVersion());
    }
    private String status(ServiceQuoteRevision r) {
        if(acceptances.findByRevisionId(r.getId()).isPresent())return "ACCEPTED";
        if(r.getRevokedAt()!=null)return "REVOKED";
        if(r.getSupersededAt()!=null)return "SUPERSEDED";
        return r.getContent().validUntil().isAfter(now())?"PUBLISHED":"EXPIRED";
    }
    private ServiceQuotePublicLink authorize(ServiceQuoteRevision r,String token) {
        var link=links.findByRevisionId(r.getId()).orElseThrow(()->error(FORBIDDEN,"QUOTE_UNAVAILABLE"));
        if(!constantEquals(link.getAccessToken(),token))throw error(FORBIDDEN,"QUOTE_UNAVAILABLE");
        return link;
    }
    private void available(ServiceQuoteRevision r, ServiceQuotePublicLink link) {
        if(!LegalConsentService.TERMS_VERSION.equals(r.getTermsVersion()) || !LegalConsentService.PRIVACY_VERSION.equals(r.getPrivacyVersion())
                || r.getRevokedAt()!=null || r.getSupersededAt()!=null || link.getRevokedAt()!=null || !r.getContent().validUntil().isAfter(now()))throw error(GONE,"QUOTE_UNAVAILABLE");
    }
    private String url(ServiceQuoteRevision r,ServiceQuotePublicLink l){return frontend+"/"+r.getContent().language()+"/quotes/"+l.getId();}
    private ServiceQuote quote(UUID id){return quotes.findById(id).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));}
    private ServiceQuote lock(UUID id){return quotes.lockById(id).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));}
    private ServiceQuoteRevision revision(UUID quoteId,UUID id){var r=revisions.findById(id).orElseThrow(()->error(NOT_FOUND,"QUOTE_UNAVAILABLE"));if(!r.getQuoteId().equals(quoteId))throw error(NOT_FOUND,"QUOTE_UNAVAILABLE");return r;}
    private void version(ServiceQuote q,long expected){if(q.getVersion()!=expected)throw error(CONFLICT,"QUOTE_VERSION");}
    private String fingerprint(Accept request){try{return hash(mapper.writeValueAsBytes(request));}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public static boolean constantEquals(String expected,String actual){return expected!=null && actual!=null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8));}
    private static String clean(String s){return s==null || s.isBlank()?null:s.trim();}
    private static OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private static ResponseStatusException error(org.springframework.http.HttpStatus status,String code){return new ResponseStatusException(status,code);}
}

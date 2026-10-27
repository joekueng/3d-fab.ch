package com.printcalculator.controller;

import com.printcalculator.dto.ServiceQuoteDtos.*;
import com.printcalculator.service.LegalConsentRequestService;
import com.printcalculator.service.servicequote.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ServiceQuoteController {
    private final ServiceQuoteService quotes;
    private final ServiceQuoteDocumentService documents;
    private final LegalConsentRequestService ips;
    private final ServiceQuoteRateLimiter limiter;
    public ServiceQuoteController(ServiceQuoteService quotes,ServiceQuoteDocumentService documents,LegalConsentRequestService ips,ServiceQuoteRateLimiter limiter){this.quotes=quotes;this.documents=documents;this.ips=ips;this.limiter=limiter;}
    @PostMapping("/service-quote-links/{linkId}/resume")
    public ResponseEntity<Resume> resume(@PathVariable UUID linkId,HttpServletRequest request){limiter.check("resume:"+ips.resolveClientIp(request),30);return privateResponse(quotes.resume(linkId));}
    @GetMapping("/service-quotes/{quoteId}/revisions/{revisionId}")
    public ResponseEntity<Offer> offer(@PathVariable UUID quoteId,@PathVariable UUID revisionId,@RequestHeader(value="X-Service-Quote-Token",required=false) String token){return privateResponse(quotes.offer(quoteId,revisionId,token));}
    @PostMapping("/service-quotes/{quoteId}/revisions/{revisionId}/accept")
    public ResponseEntity<Confirmation> accept(@PathVariable UUID quoteId,@PathVariable UUID revisionId,
            @RequestHeader(value="X-Service-Quote-Token",required=false) String token,@Valid @RequestBody Accept payload,HttpServletRequest request){
        String ip=ips.resolveClientIp(request);limiter.check("accept:"+ip,10);
        return privateResponse(quotes.accept(quoteId,revisionId,token,payload,ip));
    }
    @GetMapping("/service-quote-acceptances/{id}/pdf-status")
    public ResponseEntity<Confirmation> status(@PathVariable UUID id,@RequestHeader(value="X-Quote-Download-Token",required=false) String token){return privateResponse(quotes.downloadStatus(id,token));}
    @GetMapping("/service-quote-acceptances/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,@RequestHeader(value="X-Quote-Download-Token",required=false) String token){quotes.authorizeDownload(id,token);return pdfResponse(documents.download(id),"preventivo-accettato.pdf");}
    public static <T> ResponseEntity<T> privateResponse(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow").body(value);}
    public static ResponseEntity<byte[]> pdfResponse(byte[] bytes,String filename){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow").contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(bytes);}
}

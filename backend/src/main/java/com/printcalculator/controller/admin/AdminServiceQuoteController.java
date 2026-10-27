package com.printcalculator.controller.admin;

import com.printcalculator.controller.ServiceQuoteController;
import com.printcalculator.dto.ServiceQuoteDtos.*;
import com.printcalculator.service.servicequote.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/service-quotes")
public class AdminServiceQuoteController {
    private final ServiceQuoteService quotes;
    private final ServiceQuoteInvitationService invitations;
    private final ServiceQuoteDocumentService documents;
    private final ServiceQuotePdfRenderer renderer;
    public AdminServiceQuoteController(ServiceQuoteService quotes,ServiceQuoteInvitationService invitations,ServiceQuoteDocumentService documents,ServiceQuotePdfRenderer renderer){this.quotes=quotes;this.invitations=invitations;this.documents=documents;this.renderer=renderer;}
    @GetMapping public ResponseEntity<List<Admin>> list(){return ServiceQuoteController.privateResponse(quotes.list());}
    @PostMapping public ResponseEntity<Admin> create(@Valid @RequestBody Draft payload){return ServiceQuoteController.privateResponse(quotes.create(payload));}
    @GetMapping("/{id}") public ResponseEntity<Admin> get(@PathVariable UUID id){return ServiceQuoteController.privateResponse(quotes.get(id));}
    @PutMapping("/{id}/draft") public ResponseEntity<Admin> draft(@PathVariable UUID id,@Valid @RequestBody Draft payload){return ServiceQuoteController.privateResponse(quotes.saveDraft(id,payload));}
    @PostMapping("/{id}/publish") public ResponseEntity<Admin> publish(@PathVariable UUID id,@Valid @RequestBody Version payload){return ServiceQuoteController.privateResponse(quotes.publish(id,payload.version()));}
    @PostMapping("/{id}/revisions/{revisionId}/revoke") public ResponseEntity<Admin> revoke(@PathVariable UUID id,@PathVariable UUID revisionId){return ServiceQuoteController.privateResponse(quotes.revoke(id,revisionId));}
    @PostMapping("/{id}/revisions/{revisionId}/email") public ResponseEntity<Map<String,String>> email(@PathVariable UUID id,@PathVariable UUID revisionId,@Valid @RequestBody EmailRequest payload){return ServiceQuoteController.privateResponse(Map.of("status",invitations.send(id,revisionId,payload.email())));}
    @GetMapping("/{id}/draft/pdf-preview") public ResponseEntity<byte[]> draftPdf(@PathVariable UUID id){return ServiceQuoteController.pdfResponse(renderer.render(quotes.draftPreview(id),null),"preventivo-bozza.pdf");}
    @GetMapping("/{id}/revisions/{revisionId}/pdf-preview") public ResponseEntity<byte[]> preview(@PathVariable UUID id,@PathVariable UUID revisionId){return ServiceQuoteController.pdfResponse(renderer.render(quotes.preview(id,revisionId),null),"preventivo-anteprima.pdf");}
    @GetMapping("/{id}/acceptances/{acceptanceId}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable UUID id,@PathVariable UUID acceptanceId){var a=quotes.adminAcceptance(id,acceptanceId);return ServiceQuoteController.pdfResponse(documents.download(acceptanceId),ServiceQuoteDocumentService.filename(quotes.preview(id,a.getRevisionId())));}
    @PostMapping("/{id}/acceptances/{acceptanceId}/pdf-retry") public ResponseEntity<Admin> retry(@PathVariable UUID id,@PathVariable UUID acceptanceId){quotes.adminAcceptance(id,acceptanceId);documents.process(acceptanceId);return ServiceQuoteController.privateResponse(quotes.get(id));}
    @PostMapping("/{id}/acceptances/{acceptanceId}/receipt-email") public ResponseEntity<Admin> receipt(@PathVariable UUID id,@PathVariable UUID acceptanceId){quotes.adminAcceptance(id,acceptanceId);documents.resend(acceptanceId);return ServiceQuoteController.privateResponse(quotes.get(id));}
}

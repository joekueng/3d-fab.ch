package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminCadInvoiceCreateRequest;
import com.printcalculator.dto.AdminCadInvoiceDto;
import com.printcalculator.dto.AdminContactRequestDetailDto;
import com.printcalculator.dto.AdminSessionStatisticsDto;
import com.printcalculator.dto.AdminUpdateContactRequestStatusRequest;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.repository.EmailLogRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.FilamentVariantStockKgRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.request.CustomQuoteRequestNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationsControllerServiceTest {

    @Mock
    private FilamentVariantStockKgRepository filamentStockRepo;
    @Mock
    private FilamentVariantRepository filamentVariantRepo;
    @Mock
    private CustomQuoteRequestRepository customQuoteRequestRepo;
    @Mock
    private CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo;
    @Mock
    private EmailLogRepository emailLogRepo;
    @Mock
    private QuoteSessionRepository quoteSessionRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private PricingPolicyRepository pricingRepo;
    @Mock
    private QuoteSessionTotalsService quoteSessionTotalsService;
    @Mock
    private QuoteSessionExpiryPolicy quoteSessionExpiryPolicy;
    @Mock
    private EmailAuditService emailAuditService;
    @Mock
    private CustomQuoteRequestNotificationService contactRequestNotificationService;
    @Mock
    private com.printcalculator.repository.QuoteSessionAttachmentRepository sessionAttachmentRepo;
    @Mock
    private com.printcalculator.service.qr.QrLinkSupportService linkSupport;

    @Mock
    private com.printcalculator.service.servicequote.ServiceQuoteService serviceQuotes;

    @InjectMocks
    private AdminOperationsControllerService service;

    @Test
    void getSessionStatistics_shouldCalculateRatiosFromReadOnlyAggregates() {
        when(quoteSessionRepo.countAllForStatistics()).thenReturn(6L);
        when(quoteSessionRepo.countWithItemsForStatistics()).thenReturn(4L);
        when(quoteSessionRepo.countLineItemsForStatistics()).thenReturn(7L);
        when(quoteSessionRepo.countConvertedForStatistics()).thenReturn(3L);
        when(quoteSessionRepo.countPaidConvertedForStatistics()).thenReturn(2L);
        when(quoteSessionRepo.countModifiedForStatistics()).thenReturn(1L);
        when(quoteSessionRepo.countExpiredWithoutPaidConversionForStatistics(any(OffsetDateTime.class)))
                .thenReturn(2L);

        AdminSessionStatisticsDto dto = service.getSessionStatistics();

        assertEquals(6L, dto.getTotalSessionCount());
        assertEquals(4L, dto.getSessionsWithItemsCount());
        assertEquals(2L, dto.getEmptySessionCount());
        assertEquals(3L, dto.getConvertedSessionCount());
        assertEquals(2L, dto.getPaidConvertedSessionCount());
        assertEquals(new BigDecimal("1.75"), dto.getAverageItemsPerActiveSession());
        assertEquals(new BigDecimal("50.00"), dto.getPaidConversionRatePercent());
        assertEquals(1L, dto.getModifiedSessionCount());
        assertEquals(2L, dto.getExpiredAbandonedSessionCount());
    }

    @Test
    void updateContactRequestStatus_withInvalidStatus_shouldReturnBadRequest() {
        UUID requestId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));

        AdminUpdateContactRequestStatusRequest payload = new AdminUpdateContactRequestStatusRequest();
        payload.setStatus("wrong");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateContactRequestStatus(requestId, payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(customQuoteRequestRepo, never()).save(any(CustomQuoteRequest.class));
    }

    @Test
    void updateContactRequestStatus_withValidStatus_shouldPersistAndReturnDetail() {
        UUID requestId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());

        CustomQuoteRequestAttachment attachment = new CustomQuoteRequestAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setOriginalFilename("drawing.stp");
        attachment.setMimeType("application/step");
        attachment.setFileSizeBytes(123L);
        attachment.setCreatedAt(OffsetDateTime.now());

        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));
        when(customQuoteRequestRepo.save(any(CustomQuoteRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customQuoteRequestAttachmentRepo.findByRequest_IdOrderByCreatedAtAsc(requestId)).thenReturn(List.of(attachment));

        AdminUpdateContactRequestStatusRequest payload = new AdminUpdateContactRequestStatusRequest();
        payload.setStatus("done");

        AdminContactRequestDetailDto dto = service.updateContactRequestStatus(requestId, payload);

        assertEquals("DONE", dto.getStatus());
        assertNotNull(dto.getUpdatedAt());
        assertEquals(1, dto.getAttachments().size());
        verify(customQuoteRequestRepo).save(request);
    }

    @Test
    void resendContactRequestEmail_withRequestEmailLog_shouldDelegateAndReturnDetail() {
        UUID requestId = UUID.randomUUID();
        UUID emailLogId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        request.setStatus("PENDING");
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());

        EmailLog emailLog = new EmailLog();
        emailLog.setId(emailLogId);
        emailLog.setContactRequest(request);
        emailLog.setEventType("CONTACT_REQUEST_CUSTOMER");

        when(customQuoteRequestRepo.findById(requestId)).thenReturn(Optional.of(request));
        when(emailLogRepo.findById(emailLogId)).thenReturn(Optional.of(emailLog));
        when(customQuoteRequestAttachmentRepo.findByRequest_IdOrderByCreatedAtAsc(requestId)).thenReturn(List.of());

        AdminContactRequestDetailDto dto = service.resendContactRequestEmail(requestId, emailLogId);

        assertEquals(requestId, dto.getId());
        verify(contactRequestNotificationService).resendNotification(request, 0, emailLog);
    }

    @Test
    void createOrUpdateCadInvoice_withMissingCadHours_shouldReturnBadRequest() {
        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createOrUpdateCadInvoice_withConvertedSession_shouldReturnConflict() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);
        session.setStatus("CONVERTED");

        when(quoteSessionRepo.findLockedById(sessionId)).thenReturn(Optional.of(session));

        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(sessionId);
        payload.setCadHours(new BigDecimal("1.0"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void createOrUpdateCadInvoice_withNewSession_shouldUsePolicyCadRate() {
        PricingPolicy policy = new PricingPolicy();
        policy.setCadCostChfPerHour(new BigDecimal("85"));

        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(quoteSessionExpiryPolicy.newExpiry()).thenReturn(OffsetDateTime.now().plusMonths(6));
        when(quoteSessionRepo.save(any(QuoteSession.class))).thenAnswer(invocation -> {
            QuoteSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            return session;
        });
        when(quoteLineItemRepo.findByQuoteSessionId(any(UUID.class))).thenReturn(List.of());
        when(quoteSessionTotalsService.compute(any(QuoteSession.class), anyList()))
                .thenReturn(new QuoteSessionTotalsService.QuoteSessionTotals(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("212.50"),
                        new BigDecimal("212.50"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("212.50"),
                        BigDecimal.ZERO
                ));

        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();
        payload.setCadHours(new BigDecimal("2.5"));
        payload.setCadHourlyRateChf(null);
        payload.setNotes("  Custom CAD work  ");
        payload.setInvoiceName("  First prototype  ");
        payload.setClientName("  Example company  ");
        payload.setCollaborationName("  Device development  ");

        AdminCadInvoiceDto dto = service.createOrUpdateCadInvoice(payload);

        assertEquals("CAD_ACTIVE", dto.getSessionStatus());
        assertEquals(new BigDecimal("2.50"), dto.getCadHours());
        assertEquals(new BigDecimal("85.00"), dto.getCadHourlyRateChf());
        assertEquals("Custom CAD work", dto.getNotes());
        assertEquals("First prototype", dto.getInvoiceName());
        assertEquals("Example company", dto.getClientName());
        assertEquals("Device development", dto.getCollaborationName());
        assertEquals(new BigDecimal("212.50"), dto.getCadTotalChf());
    }

    @Test
    void metadataCanChangeAfterCheckoutWithoutChangingOrderPrices() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setStatus("CONVERTED");
        session.setCadHours(new BigDecimal("2"));
        session.setCadHourlyRateChf(new BigDecimal("90"));
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));

        service.updateCadInvoiceMetadata(id, new com.printcalculator.dto.AdminCadInvoiceMetadataRequest(
                "  Iteration 1  ", " ACME ", " Product A "));
        assertEquals("Iteration 1", session.getInvoiceName());
        assertEquals("ACME", session.getClientName());
        assertEquals("Product A", session.getCollaborationName());
        assertEquals(new BigDecimal("2"), session.getCadHours());
        assertEquals(new BigDecimal("90"), session.getCadHourlyRateChf());
        org.mockito.Mockito.verifyNoInteractions(orderRepo);

        service.updateCadInvoiceMetadata(id, new com.printcalculator.dto.AdminCadInvoiceMetadataRequest(" ", null, ""));
        org.junit.jupiter.api.Assertions.assertNull(session.getInvoiceName());
        org.junit.jupiter.api.Assertions.assertNull(session.getClientName());
        org.junit.jupiter.api.Assertions.assertNull(session.getCollaborationName());
    }

    @Test
    void serviceRowsReplaceLegacyCadAndRemainListedAfterConversion() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_ACTIVE");
        session.setCadHours(new BigDecimal("8"));
        session.setCadHourlyRateChf(new BigDecimal("100"));
        var line = new com.printcalculator.dto.ServiceLineDto("Meeting and revisions",
                com.printcalculator.dto.ServiceLineDto.BillingType.FIXED, BigDecimal.ONE, new BigDecimal("150"));
        AdminCadInvoiceCreateRequest payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(id);
        payload.setServiceLines(List.of(line));
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        when(quoteSessionRepo.save(session)).thenReturn(session);
        when(quoteLineItemRepo.findByQuoteSessionId(id)).thenReturn(List.of());
        when(quoteSessionTotalsService.compute(session, List.of())).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("150"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("150"), BigDecimal.ZERO));
        var result = service.createOrUpdateCadInvoice(payload);
        assertEquals(List.of(line), result.getServiceLines());
        verify(quoteLineItemRepo, never()).deleteByQuoteSession_Id(id);
        assertEquals(BigDecimal.ZERO, session.getCadHours());
        assertEquals(BigDecimal.ZERO, session.getCadHourlyRateChf());
        session.setStatus("CONVERTED");
        when(quoteSessionRepo.findByStatusInOrderByCreatedAtDesc(List.of("CAD_ACTIVE", "CONVERTED")))
                .thenReturn(List.of(session));
        assertEquals(1, service.getCadInvoices().size());
    }

    @Test
    void deleteQuoteSession_whenLinkedToOrder_shouldReturnConflict() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(sessionId);

        when(quoteSessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
        when(orderRepo.existsBySourceQuoteSession_Id(sessionId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteQuoteSession(sessionId)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(quoteSessionRepo, never()).delete(any(QuoteSession.class));
    }

    @Test
    void deletingDraftReservesItsLinkAndRemovesItsContent() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_ACTIVE");
        session.setNotes("Private instructions");
        session.setClientName("Customer");
        session.setInformationDraftId(UUID.randomUUID());
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        when(sessionAttachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(id)).thenReturn(List.of());

        service.deleteCadInvoice(id, new com.printcalculator.dto.AdminCadInvoiceDeleteRequest(
                com.printcalculator.dto.AdminCadInvoiceDeleteRequest.LinkAction.NEW_INVOICE, null));

        assertEquals("CAD_DELETED", session.getStatus());
        assertEquals(id, session.getId());
        org.junit.jupiter.api.Assertions.assertNull(session.getNotes());
        org.junit.jupiter.api.Assertions.assertNull(session.getClientName());
        org.junit.jupiter.api.Assertions.assertNull(session.getInformationDraftId());
        org.junit.jupiter.api.Assertions.assertNull(session.getDeletedRedirectPath());
        assertEquals(List.of(), session.getServiceLines());
        verify(quoteLineItemRepo).deleteByQuoteSession_Id(id);
        verify(sessionAttachmentRepo).deleteAll(List.of());
        verify(quoteSessionRepo, never()).delete(any());
    }

    @Test
    void deletingDraftRedirectsThroughExistingPublicPathRules() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_ACTIVE");
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        when(linkSupport.normalizeTargetPath("/it/contact")).thenReturn("/contact");
        when(sessionAttachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(id)).thenReturn(List.of());
        service.deleteCadInvoice(id, new com.printcalculator.dto.AdminCadInvoiceDeleteRequest(
                com.printcalculator.dto.AdminCadInvoiceDeleteRequest.LinkAction.REDIRECT, "/it/contact"));
        assertEquals("CAD_DELETED", session.getStatus());
        assertEquals("/contact", session.getDeletedRedirectPath());
    }

    @Test
    void deletionRejectsOrderedInvoicesAndUnsafeTargetsBeforeRemovingAnything() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CONVERTED");
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        var request = new com.printcalculator.dto.AdminCadInvoiceDeleteRequest(
                com.printcalculator.dto.AdminCadInvoiceDeleteRequest.LinkAction.REDIRECT, "/checkout/cad");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.deleteCadInvoice(id, request)).getStatusCode());
        session.setStatus("CAD_ACTIVE");
        when(linkSupport.normalizeTargetPath("/checkout/cad")).thenReturn("/checkout/cad");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.deleteCadInvoice(id, request)).getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(quoteLineItemRepo, sessionAttachmentRepo);
    }

    @Test
    void updateKeepsSessionIdWhenReusingADeletedInvoiceLink() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_DELETED");
        var payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(id);
        payload.setCadHours(BigDecimal.ONE);
        payload.setCadHourlyRateChf(new BigDecimal("80"));
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        when(quoteSessionRepo.save(session)).thenReturn(session);
        when(quoteSessionExpiryPolicy.newExpiry()).thenReturn(OffsetDateTime.now().plusMonths(3));
        when(quoteLineItemRepo.findByQuoteSessionId(id)).thenReturn(List.of());
        when(quoteSessionTotalsService.compute(session, List.of())).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("80"), new BigDecimal("80"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("80"), BigDecimal.ZERO));
        var result = service.createOrUpdateCadInvoice(payload);
        assertEquals(id, result.getSessionId());
        assertEquals("/checkout/cad?session=" + id, result.getCheckoutPath());
        assertEquals("CAD_ACTIVE", session.getStatus());
        session.setStatus("CAD_DELETED");
        session.setDeletedRedirectPath("/contact");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)).getStatusCode());
    }
    @Test
    void detachingCalculatorKeepsCheckoutServicesNotesAttachmentsAndOriginalPrintData() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CAD_ACTIVE");
        session.setSetupCostChf(new BigDecimal("5"));
        var line = new com.printcalculator.dto.ServiceLineDto("Design",
                com.printcalculator.dto.ServiceLineDto.BillingType.FIXED, BigDecimal.ONE, new BigDecimal("150"));
        var payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(id);
        payload.setServiceLines(List.of(line));
        payload.setNotes("Keep these instructions");
        payload.setCalculatorSessionId(null);
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        when(quoteSessionRepo.save(session)).thenReturn(session);
        // Use the real totals calculation to catch residual setup, print or shipping charges.
        var calculator = org.mockito.Mockito.mock(com.printcalculator.service.QuoteCalculator.class);
        when(calculator.calculateSessionMachineCost(any(), any())).thenReturn(BigDecimal.ZERO);
        var totalsService = new QuoteSessionTotalsService(pricingRepo, calculator,
                org.mockito.Mockito.mock(com.printcalculator.repository.NozzleOptionRepository.class),
                org.mockito.Mockito.mock(com.printcalculator.repository.PricingPolicyMachineHourTierRepository.class),
                new com.printcalculator.service.ShippingQuoteService(3,
                        com.printcalculator.service.ShippingQuoteService.DEFAULT_PROFILES));
        when(quoteSessionTotalsService.compute(session, List.of()))
                .thenAnswer(invocation -> totalsService.compute(session, List.of()));

        var result = service.createOrUpdateCadInvoice(payload);

        assertEquals(id, result.getSessionId());
        assertEquals("/checkout/cad?session=" + id, result.getCheckoutPath());
        assertEquals(List.of(line), result.getServiceLines());
        assertEquals("Keep these instructions", result.getNotes());
        assertEquals(0, result.getGrandTotalChf().compareTo(new BigDecimal("150")));
        assertEquals(0, result.getPrintItemsTotalChf().signum());
        assertEquals(0, result.getSetupCostChf().signum());
        assertEquals(0, result.getShippingCostChf().signum());
        assertEquals(new BigDecimal("5"), session.getSetupCostChf());
        assertEquals(Boolean.TRUE, session.getCalculatorDetached());
        org.mockito.Mockito.verifyNoInteractions(quoteLineItemRepo);
        org.mockito.Mockito.verifyNoInteractions(sessionAttachmentRepo);
        verify(quoteSessionRepo, never()).delete(any());
    }

    @Test
    void changingCalculatorRejectsConvertedAndOrderLinkedInvoices() {
        UUID id = UUID.randomUUID();
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("CONVERTED");
        var payload = new AdminCadInvoiceCreateRequest();
        payload.setSessionId(id);
        payload.setCadHours(BigDecimal.ONE);
        payload.setCadHourlyRateChf(BigDecimal.TEN);
        payload.setCalculatorSessionId(null);
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(session));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)).getStatusCode());
        session.setStatus("CAD_ACTIVE");
        when(orderRepo.existsBySourceQuoteSession_Id(id)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)).getStatusCode());
        org.mockito.Mockito.verifyNoInteractions(quoteLineItemRepo, sessionAttachmentRepo);
        verify(quoteSessionRepo, never()).save(any());
    }

    @Test
    void calculatorCanBeSwitchedClearedAndRestoredWithoutChangingInvoiceIdentity() {
        UUID id = UUID.randomUUID();
        QuoteSession invoice = new QuoteSession();
        invoice.setId(id); invoice.setStatus("CAD_ACTIVE");
        invoice.setSetupCostChf(new BigDecimal("5"));
        QuoteSession source = new QuoteSession();
        source.setId(UUID.randomUUID()); source.setStatus("ACTIVE"); source.setSessionType("PRINT_QUOTE");
        source.setSetupCostChf(new BigDecimal("9"));
        var ownItem = new com.printcalculator.entity.QuoteLineItem(); ownItem.setQuoteSession(invoice);
        var sourceItem = new com.printcalculator.entity.QuoteLineItem(); sourceItem.setQuoteSession(source);
        when(quoteSessionRepo.findLockedById(id)).thenReturn(Optional.of(invoice));
        when(quoteSessionRepo.findLockedById(source.getId())).thenReturn(Optional.of(source));
        when(quoteSessionRepo.save(invoice)).thenReturn(invoice);
        when(quoteLineItemRepo.findByQuoteSessionId(id)).thenReturn(List.of(ownItem));
        when(quoteLineItemRepo.findByQuoteSessionId(source.getId())).thenReturn(List.of(sourceItem));
        when(quoteSessionTotalsService.compute(any(), anyList())).thenReturn(
                new QuoteSessionTotalsService.QuoteSessionTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, BigDecimal.ZERO));
        var payload = new AdminCadInvoiceCreateRequest(); payload.setSessionId(id);
        payload.setCadHours(BigDecimal.ONE); payload.setCadHourlyRateChf(BigDecimal.TEN);
        for (UUID selected : new UUID[]{source.getId(), null, id, source.getId()}) {
            payload.setCalculatorSessionId(selected);
            var result = service.createOrUpdateCadInvoice(payload);
            assertEquals(id, result.getSessionId());
            assertEquals("/checkout/cad?session=" + id, result.getCheckoutPath());
            assertEquals(selected, result.getCalculatorSessionId());
            assertEquals(selected == null ? null : selected.equals(id) ? invoice : source, invoice.getPrintSource());
        }
        assertEquals(new BigDecimal("5"), invoice.getSetupCostChf());
        assertEquals(new BigDecimal("9"), source.getSetupCostChf());
        assertEquals("ACTIVE", source.getStatus());
        verify(quoteLineItemRepo, never()).deleteByQuoteSession_Id(any());
        org.mockito.Mockito.verifyNoInteractions(sessionAttachmentRepo);
    }

    @Test
    void invalidCalculatorAssociationDoesNotChangeTheInvoice() {
        var invoice = new QuoteSession(); invoice.setId(UUID.randomUUID()); invoice.setStatus("CAD_ACTIVE");
        var source = new QuoteSession(); source.setId(UUID.randomUUID()); source.setStatus("CAD_ACTIVE");
        source.setSessionType("PRINT_QUOTE");
        var payload = new AdminCadInvoiceCreateRequest(); payload.setSessionId(invoice.getId());
        payload.setCadHours(BigDecimal.ONE); payload.setCadHourlyRateChf(BigDecimal.TEN);
        payload.setCalculatorSessionId(source.getId());
        when(quoteSessionRepo.findLockedById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(quoteSessionRepo.findLockedById(source.getId())).thenReturn(Optional.of(source));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.createOrUpdateCadInvoice(payload)).getStatusCode());
        org.junit.jupiter.api.Assertions.assertNull(invoice.getCalculatorSession());
        verify(quoteSessionRepo, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(quoteLineItemRepo, sessionAttachmentRepo);
    }

    @Test
    void commercialReferenceIsPreservedWhenOmittedAndClearedOnlyWhenExplicitlyNull() {
        var invoice=new QuoteSession();invoice.setId(UUID.randomUUID());invoice.setStatus("CAD_ACTIVE");
        var quoteId=UUID.randomUUID();var revisionId=UUID.randomUUID();
        var reference=new com.printcalculator.dto.ServiceQuoteReference(quoteId,revisionId,UUID.randomUUID(),1,"Design",BigDecimal.TEN,new BigDecimal("12"),"PERCENTAGE",new BigDecimal("20"));
        when(quoteSessionRepo.findLockedById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(quoteSessionRepo.save(invoice)).thenReturn(invoice);
        when(quoteLineItemRepo.findByQuoteSessionId(invoice.getId())).thenReturn(List.of());
        when(quoteSessionTotalsService.compute(any(),anyList())).thenReturn(new QuoteSessionTotalsService.QuoteSessionTotals(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO));
        when(serviceQuotes.resolveAccepted(quoteId,revisionId)).thenReturn(reference);
        var attach=new AdminCadInvoiceCreateRequest();attach.setSessionId(invoice.getId());attach.setCadHours(BigDecimal.ONE);attach.setCadHourlyRateChf(BigDecimal.TEN);
        attach.setServiceQuoteId(quoteId);attach.setServiceQuoteRevisionId(revisionId);
        assertEquals(reference,service.createOrUpdateCadInvoice(attach).getServiceQuoteReference());
        var token=invoice.getCadCheckoutCustomerToken();
        org.junit.jupiter.api.Assertions.assertNotNull(token);
        var same=service.createOrUpdateCadInvoice(attach);
        assertEquals(token,invoice.getCadCheckoutCustomerToken());
        org.junit.jupiter.api.Assertions.assertTrue(same.getCheckoutPath().endsWith("#prefill="+token));
        var omit=new AdminCadInvoiceCreateRequest();omit.setSessionId(invoice.getId());omit.setCadHours(BigDecimal.ONE);omit.setCadHourlyRateChf(BigDecimal.TEN);
        assertEquals(reference,service.createOrUpdateCadInvoice(omit).getServiceQuoteReference());
        var another=new com.printcalculator.dto.ServiceQuoteReference(quoteId,UUID.randomUUID(),UUID.randomUUID(),2,"Revised design",BigDecimal.TEN,new BigDecimal("12"),"PERCENTAGE",new BigDecimal("20"));
        when(serviceQuotes.resolveAccepted(quoteId,revisionId)).thenReturn(another);
        service.createOrUpdateCadInvoice(attach);
        org.junit.jupiter.api.Assertions.assertNotEquals(token,invoice.getCadCheckoutCustomerToken());
        omit.setServiceQuoteId(null);
        org.junit.jupiter.api.Assertions.assertNull(service.createOrUpdateCadInvoice(omit).getServiceQuoteReference());
        org.junit.jupiter.api.Assertions.assertNull(invoice.getServiceQuoteId());
        org.junit.jupiter.api.Assertions.assertNull(invoice.getCadCheckoutCustomerToken());
        verify(serviceQuotes,org.mockito.Mockito.times(3)).resolveAccepted(quoteId,revisionId);
    }

}

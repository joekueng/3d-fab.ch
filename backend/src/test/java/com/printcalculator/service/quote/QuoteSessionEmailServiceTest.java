package com.printcalculator.service.quote;

import com.printcalculator.dto.*;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.email.*;
import com.printcalculator.service.information.OrderInformationService;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteSessionEmailServiceTest {
    private final QuoteSessionRepository repo = mock(QuoteSessionRepository.class);
    private final OrderInformationService information = mock(OrderInformationService.class);
    private final EmailNotificationService mail = mock(EmailNotificationService.class);
    private final EmailAuditService audit = mock(EmailAuditService.class);
    private final QuoteSessionExpiryPolicy expiryPolicy = mock(QuoteSessionExpiryPolicy.class);
    private QuoteSessionEmailService service;
    private QuoteSession session;
    private QuoteSessionEmailRequest request;
    private OffsetDateTime renewedExpiry;
    @BeforeEach void setup() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        renewedExpiry = OffsetDateTime.now().plusMonths(3);
        when(expiryPolicy.newExpiry()).thenReturn(renewedExpiry);
        service = new QuoteSessionEmailService(repo, information, mail, audit, manager, expiryPolicy,
                "https://example.test/");
        session = new QuoteSession();
        session.setId(UUID.randomUUID()); session.setInformationDraftId(UUID.randomUUID());
        session.setStatus("ACTIVE"); session.setExpiresAt(OffsetDateTime.now().plusMonths(6));
        request = new QuoteSessionEmailRequest("customer@example.test", "it", "easy",
                new InformationDto.DraftLink(session.getInformationDraftId(), "private-key"));
        when(repo.findLockedById(session.getId())).thenReturn(Optional.of(session));
        when(repo.findById(session.getId())).thenReturn(Optional.of(session));
        when(mail.sendEmail(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(EmailSendResult.sent(OffsetDateTime.now(), OffsetDateTime.now()));
    }
    @Test void scansBeforeSendingACompleteLocalizedLink() {
        service.send(session.getId(), request);
        var order = inOrder(information, mail, audit);
        order.verify(information).scanDraft(session.getInformationDraftId(), "private-key");
        order.verify(information).link(session, request.information());
        order.verify(mail).sendEmail(eq(request.email()), anyString(), eq("quote-session"), argThat(context ->
                context.get("resumeUrl").equals("https://example.test/it/calculator/basic?session=" + session.getId())
                        && context.containsKey("expiresAt")));
        order.verify(audit).recordSessionEmail(eq(request.email()), anyString(), any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"it", "en", "de", "fr"})
    void rendersLocalizedEmailWithBrandingAndWorkingLink(String language) {
        var localized = new QuoteSessionEmailRequest(request.email(), language, "advanced", request.information());
        service.send(session.getId(), localized);
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.captor();
        verify(mail).sendEmail(eq(request.email()), anyString(), eq("quote-session"), captor.capture());
        var data = captor.getValue();
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new org.thymeleaf.context.Context();
        context.setVariables(data);
        var document = org.jsoup.Jsoup.parse(engine.process("email/quote-session", context));
        assertEquals(language, document.selectFirst("html").attr("lang"));
        assertEquals(data.get("title"), document.selectFirst(".header h1").text());
        assertEquals("https://example.test/assets/images/SVG/logo-giallo-spesso.svg", document.selectFirst(".brand-logo").attr("src"));
        assertEquals("https://example.test/" + language + "/calculator/advanced?session=" + session.getId(),
                document.selectFirst(".content a").attr("href"));
        assertEquals(data.get("action"), document.selectFirst(".action-button").text());
        assertEquals(data.get("expiresAt"), document.selectFirst(".content strong").text());
        assertEquals(data.get("notice"), document.select(".footer p").last().text());
        assertTrue(document.selectFirst(".footer").text().contains(String.valueOf(java.time.Year.now().getValue())));
        for (String key : List.of("title", "intro", "action", "expiryLabel", "notice")) {
            assertFalse(data.get(key).toString().isBlank());
            assertTrue(document.text().contains(data.get(key).toString()));
        }
    }
    @Test void neverSendsWhenScanFails() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .when(information).scanDraft(any(), any());
        assertThrows(ResponseStatusException.class, () -> service.send(session.getId(), request));
        verifyNoInteractions(mail);
    }
    @Test void copiedLinkMatchesEmailedLinkWithoutSendingEmail() {
        String link = service.createLink(session.getId(), new QuoteSessionLinkRequest("it", "easy", request.information()));
        verify(information).scanDraft(session.getInformationDraftId(), "private-key");
        verifyNoInteractions(mail);
        service.send(session.getId(), request);
        verify(mail).sendEmail(anyString(), anyString(), anyString(), argThat(context -> context.get("resumeUrl").equals(link)));
    }
    @Test void refusesReplacingAnotherDraft() {
        var foreign = new QuoteSessionEmailRequest(request.email(), "en", "easy",
                new InformationDto.DraftLink(UUID.randomUUID(), "foreign"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.send(session.getId(), foreign)).getStatusCode().value());
        verifyNoInteractions(information, mail);
    }
    @Test void rejectsExpiredAndConvertedLinks() {
        session.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertEquals(410, assertThrows(ResponseStatusException.class, () -> service.resume(session.getId())).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.send(session.getId(), request));
        session.setExpiresAt(OffsetDateTime.now().plusMonths(1)); session.setStatus("CONVERTED");
        assertThrows(ResponseStatusException.class, () -> service.resume(session.getId()));
        verifyNoInteractions(mail);
    }
    @Test void rejectsCreatingOrResumingALinkAfterOrderConversion() {
        session.setConvertedOrderId(UUID.randomUUID());
        var linkRequest = new QuoteSessionLinkRequest("en", "easy", request.information());
        assertEquals(410, assertThrows(ResponseStatusException.class,
                () -> service.createLink(session.getId(), linkRequest)).getStatusCode().value());
        assertEquals(410, assertThrows(ResponseStatusException.class,
                () -> service.resume(session.getId())).getStatusCode().value());
        verifyNoInteractions(information, mail);
    }
    @Test void resolvesThePersistedDraftFromTheNormalSessionLink() {
        var credential = new InformationDto.Credential(session.getInformationDraftId(), "private-key");
        when(information.sessionCredential(session)).thenReturn(credential);
        assertEquals(credential, service.resume(session.getId()));
        assertEquals(renewedExpiry, session.getExpiresAt());
        verify(expiryPolicy).newExpiry();
        verify(information).sessionCredential(session);
    }
    @Test void throttlesRepeatedSessionAndRecipient() {
        service.send(session.getId(), request);
        assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.send(session.getId(), request)).getStatusCode().value());
        verify(mail, times(1)).sendEmail(anyString(), anyString(), anyString(), anyMap());
    }
    @Test void reportsFailedAndDisabledEmailAsFailure() {
        when(mail.sendEmail(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(EmailSendResult.skipped(OffsetDateTime.now(), "disabled"));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> service.send(session.getId(), request)).getStatusCode().value());
        verify(audit).recordSessionEmail(anyString(), anyString(), any());
    }
}

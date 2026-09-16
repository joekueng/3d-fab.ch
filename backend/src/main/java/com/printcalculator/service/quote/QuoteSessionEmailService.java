package com.printcalculator.service.quote;

import com.printcalculator.dto.QuoteSessionEmailRequest;
import com.printcalculator.dto.QuoteSessionLinkRequest;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.email.EmailSendResult;
import com.printcalculator.service.information.OrderInformationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class QuoteSessionEmailService {
    private final QuoteSessionRepository sessions;
    private final OrderInformationService information;
    private final EmailNotificationService email;
    private final TransactionTemplate transactions;
    private final QuoteSessionExpiryPolicy expiryPolicy;
    private final com.printcalculator.service.email.EmailAuditService audit;
    private final String frontend;
    private final Map<String, Long> attempts = new HashMap<>();

    public QuoteSessionEmailService(QuoteSessionRepository sessions, OrderInformationService information,
            EmailNotificationService email, com.printcalculator.service.email.EmailAuditService audit,
            org.springframework.transaction.PlatformTransactionManager manager, QuoteSessionExpiryPolicy expiryPolicy,
            @Value("${app.frontend.base-url}") String frontend) {
        this.sessions = sessions;
        this.information = information;
        this.email = email;
        this.audit = audit;
        this.transactions = new TransactionTemplate(manager);
        this.expiryPolicy = expiryPolicy;
        this.frontend = frontend.replaceAll("/+$", "");
    }

    private record PreparedLink(String url, String expiry) {}

    private PreparedLink prepareLink(UUID id, QuoteSessionLinkRequest request) {
        // Commit the information association before sending a link that another device can open.
        String expiry = transactions.execute(status -> {
            var session = sessions.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now())
                    || session.getConvertedOrderId() != null || !Set.of("ACTIVE", "CAD_ACTIVE").contains(session.getStatus())) {
                throw new ResponseStatusException(GONE, "SESSION_UNAVAILABLE");
            }
            if (session.getInformationDraftId() != null
                    && !session.getInformationDraftId().equals(request.information().id())) {
                throw new ResponseStatusException(FORBIDDEN);
            }
            information.scanDraft(request.information().id(), request.information().token());
            information.link(session, request.information());
            return session.getExpiresAt().atZoneSameInstant(java.time.ZoneId.of("Europe/Zurich"))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z"));
        });
        String path = request.mode().equals("advanced") ? "advanced" : "basic";
        String link = frontend + "/" + request.language() + "/calculator/" + path + "?session=" + id;
        return new PreparedLink(link, expiry);
    }

    public String createLink(UUID id, QuoteSessionLinkRequest request) {
        return prepareLink(id, request).url();
    }

    public void send(UUID id, QuoteSessionEmailRequest request) {
        PreparedLink prepared = prepareLink(id, new QuoteSessionLinkRequest(request.language(), request.mode(), request.information()));
        reserve(id, request.email());
        String[] copy = copy(request.language());
        var result = email.sendEmail(request.email().trim(), copy[0], "quote-session",
                Map.of("language", request.language(), "title", copy[0], "intro", copy[1],
                        "action", copy[2], "expiryLabel", copy[3], "notice", copy[4],
                        "expiresAt", prepared.expiry(), "resumeUrl", prepared.url(),
                        "logoUrl", frontend + "/assets/images/SVG/logo-giallo-spesso.svg",
                        "currentYear", java.time.Year.now().getValue()));
        audit.recordSessionEmail(request.email().trim(), copy[0], result);
        if (result == null || !EmailSendResult.STATUS_SENT.equals(result.status())) {
            throw new ResponseStatusException(BAD_GATEWAY, "SESSION_EMAIL_FAILED");
        }
    }

    public void linkInformation(UUID id, com.printcalculator.dto.InformationDto.DraftLink link) {
        transactions.executeWithoutResult(status -> {
            var session = sessions.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now())
                    || session.getConvertedOrderId() != null || !Set.of("ACTIVE", "CAD_ACTIVE").contains(session.getStatus())) {
                throw new ResponseStatusException(GONE, "SESSION_UNAVAILABLE");
            }
            if (session.getInformationDraftId() != null && !session.getInformationDraftId().equals(link.id())) {
                throw new ResponseStatusException(FORBIDDEN);
            }
            information.link(session, link);
        });
    }

    public com.printcalculator.dto.InformationDto.Credential resume(UUID id) {
        return transactions.execute(status -> {
            var session = sessions.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now())
                    || session.getConvertedOrderId() != null || !Set.of("ACTIVE", "CAD_ACTIVE").contains(session.getStatus())) {
                throw new ResponseStatusException(GONE, "SESSION_UNAVAILABLE");
            }
            session.setExpiresAt(expiryPolicy.newExpiry());
            return information.sessionCredential(session);
        });
    }

    private synchronized void reserve(UUID id, String email) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> entry.getValue() <= now - 60_000);
        String recipient = "email:" + email.trim().toLowerCase(Locale.ROOT);
        String session = "session:" + id;
        if (attempts.containsKey(recipient) || attempts.containsKey(session) || attempts.size() >= 10_000) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "SESSION_EMAIL_RATE_LIMIT");
        }
        attempts.put(recipient, now);
        attempts.put(session, now);
    }

    private String[] copy(String language) {
        return switch (language) {
            case "de" -> new String[]{"Deine gespeicherte Drucksitzung", "Modelle, Einstellungen, Anweisungen und Anhänge sind gespeichert.", "Sitzung fortsetzen", "Verfügbar bis", "Wer diesen Link besitzt, kann auf die Sitzung zugreifen. Nach der Bestellung ist der Link nicht mehr gültig."};
            case "fr" -> new String[]{"Votre session d’impression enregistrée", "Vos modèles, réglages, instructions et pièces jointes sont enregistrés.", "Reprendre la session", "Disponible jusqu’au", "Toute personne disposant de ce lien peut accéder à la session. Le lien devient invalide après la commande."};
            case "en" -> new String[]{"Your saved printing session", "Your models, settings, instructions and attachments are saved.", "Resume session", "Available until", "Anyone with this link can access the session. The link is no longer valid after ordering."};
            default -> new String[]{"La tua sessione di stampa salvata", "Modelli, impostazioni, istruzioni e allegati sono salvati.", "Riprendi la sessione", "Disponibile fino al", "Chi possiede questo link può accedere alla sessione. Il link non è più valido dopo l’ordine."};
        };
    }
}

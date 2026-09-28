package com.printcalculator.service.email;

import com.printcalculator.dto.ReviewRequestPreviewDto;
import com.printcalculator.entity.EmailLog;
import com.printcalculator.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewRequestEmailService {
    private final EmailNotificationService emailNotificationService;
    private final EmailAuditService emailAuditService;

    @Value("${app.mail.google-review-url:https://g.page/r/CXamfIi-St1wEAI/review}")
    private String reviewUrl;

    public ReviewRequestPreviewDto preview(Order order) {
        String language = language(order.getPreferredLanguage());
        String recipient = order.getCustomer() != null && order.getCustomer().getEmail() != null
                && !order.getCustomer().getEmail().isBlank()
                ? order.getCustomer().getEmail() : order.getCustomerEmail();
        String name = order.getCustomer() != null && order.getCustomer().getFirstName() != null
                && !order.getCustomer().getFirstName().isBlank()
                ? order.getCustomer().getFirstName() : order.getBillingFirstName();
        if (name == null) name = "";
        String greeting = switch (language) {
            case "en" -> name.isBlank() ? "Hello," : "Hello " + name + ",";
            case "de" -> name.isBlank() ? "Guten Tag," : "Hallo " + name + ",";
            case "fr" -> name.isBlank() ? "Bonjour," : "Bonjour " + name + ",";
            default -> name.isBlank() ? "Ciao," : "Ciao " + name + ",";
        };
        return switch (language) {
            case "en" -> new ReviewRequestPreviewDto(recipient, "How was your experience with 3D-Fab?", "Thank you for choosing 3D-Fab", greeting,
                    "Thank you for choosing 3D-Fab for your project.",
                    "If you would like, tell us about your experience in a Google review. Your feedback helps us improve our service.",
                    "You can also add photos of your project if you wish.", "Write a Google review", reviewUrl,
                    "Thank you for your trust. If you need help, you can reply to this email.", "Joe and Matteo", "A personal request from 3D-Fab.");
            case "de" -> new ReviewRequestPreviewDto(recipient, "Wie war Ihre Erfahrung mit 3D-Fab?", "Danke, dass Sie 3D-Fab gewählt haben", greeting,
                    "Vielen Dank, dass Sie 3D-Fab für Ihr Projekt gewählt haben.",
                    "Wenn Sie möchten, erzählen Sie in einer Google-Rezension von Ihrer Erfahrung. Ihr Feedback hilft uns, unseren Service zu verbessern.",
                    "Sie können auf Wunsch auch Fotos Ihres Projekts hinzufügen.", "Google-Rezension schreiben", reviewUrl,
                    "Vielen Dank für Ihr Vertrauen. Wenn Sie Hilfe benötigen, antworten Sie einfach auf diese E-Mail.", "Joe und Matteo", "Eine persönliche Anfrage von 3D-Fab.");
            case "fr" -> new ReviewRequestPreviewDto(recipient, "Comment s'est passée votre expérience avec 3D-Fab ?", "Merci d'avoir choisi 3D-Fab", greeting,
                    "Merci d'avoir choisi 3D-Fab pour votre projet.",
                    "Si vous le souhaitez, racontez votre expérience dans un avis Google. Votre retour nous aide à améliorer notre service.",
                    "Vous pouvez aussi ajouter des photos de votre projet si vous le souhaitez.", "Laisser un avis Google", reviewUrl,
                    "Merci de votre confiance. Si vous avez besoin d'aide, répondez simplement à cet e-mail.", "Joe et Matteo", "Une demande personnelle de 3D-Fab.");
            default -> new ReviewRequestPreviewDto(recipient, "Com'è stata la tua esperienza con 3D-Fab?", "Grazie per aver scelto 3D-Fab", greeting,
                    "Grazie per aver scelto 3D-Fab per il tuo progetto.",
                    "Se ti va, racconta la tua esperienza in una recensione su Google. La tua opinione ci aiuta a migliorare il servizio.",
                    "Se desideri, puoi aggiungere anche delle foto del tuo progetto.", "Lascia una recensione su Google", reviewUrl,
                    "Grazie per la fiducia. Se hai bisogno di aiuto, puoi rispondere a questa email.", "Joe e Matteo", "Una richiesta personale di 3D-Fab.");
        };
    }

    public EmailLog send(Order order) {
        ReviewRequestPreviewDto copy = preview(order);
        Map<String, Object> context = new HashMap<>();
        context.put("emailTitle", copy.subject());
        context.put("headlineText", copy.headline());
        context.put("greetingText", copy.greeting());
        context.put("introText", copy.intro());
        context.put("requestText", copy.request());
        context.put("photoNoteText", copy.photoNote());
        context.put("actionText", copy.actionText());
        context.put("reviewUrl", copy.reviewUrl());
        context.put("closingText", copy.closing());
        context.put("signatureText", copy.signature());
        context.put("footerText", copy.footer());
        context.put("currentYear", Year.now().getValue());
        EmailSendResult result = emailNotificationService.sendEmail(copy.recipient(), copy.subject(), "review-request", context);
        return emailAuditService.recordOrderEmail(order, EmailAuditService.EVENT_GOOGLE_REVIEW_REQUEST_CUSTOMER,
                EmailAuditService.ORIGIN_ADMIN, copy.recipient(), copy.subject(), "review-request", null, result, null);
    }

    private String language(String preferredLanguage) {
        if (preferredLanguage == null) return "it";
        String normalized = preferredLanguage.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 2) normalized = normalized.substring(0, 2);
        return switch (normalized) {
            case "it", "en", "de", "fr" -> normalized;
            default -> "it";
        };
    }
}

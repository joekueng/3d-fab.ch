package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ContactRequestLocalizationService {

    public String applyCustomerContactRequestTexts(Map<String, Object> templateData, String language, UUID requestId) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Contact request received");
                templateData.put("headlineText", "We received your contact request");
                templateData.put("greetingText", "Hi " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Thank you for contacting us. Our team will reply as soon as possible.");
                templateData.put("requestIdHintText", "Please keep this request ID for future order references:");
                templateData.put("detailsTitleText", "Request details");
                templateData.put("labelRequestId", "Request ID");
                templateData.put("labelDate", "Date");
                templateData.put("labelRequestType", "Request type");
                templateData.put("labelCustomerType", "Customer type");
                templateData.put("labelName", "Name");
                templateData.put("labelCompany", "Company");
                templateData.put("labelContactPerson", "Contact person");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Phone");
                templateData.put("labelMessage", "Message");
                templateData.put("labelAttachments", "Attachments");
                templateData.put("supportText", "If you need help, reply to this email.");
                templateData.put("footerText", "Automated request-receipt confirmation from 3D-Fab.");
                yield "We received your contact request #" + requestId + " - 3D-Fab";
            }
            case "de" -> {
                templateData.put("emailTitle", "Kontaktanfrage erhalten");
                templateData.put("headlineText", "Wir haben Ihre Kontaktanfrage erhalten");
                templateData.put("greetingText", "Hallo " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Vielen Dank fuer Ihre Anfrage. Unser Team antwortet Ihnen so schnell wie moeglich.");
                templateData.put("requestIdHintText", "Bitte speichern Sie diese Anfrage-ID fuer zukuenftige Bestellreferenzen:");
                templateData.put("detailsTitleText", "Anfragedetails");
                templateData.put("labelRequestId", "Anfrage-ID");
                templateData.put("labelDate", "Datum");
                templateData.put("labelRequestType", "Anfragetyp");
                templateData.put("labelCustomerType", "Kundentyp");
                templateData.put("labelName", "Name");
                templateData.put("labelCompany", "Firma");
                templateData.put("labelContactPerson", "Kontaktperson");
                templateData.put("labelEmail", "E-Mail");
                templateData.put("labelPhone", "Telefon");
                templateData.put("labelMessage", "Nachricht");
                templateData.put("labelAttachments", "Anhaenge");
                templateData.put("supportText", "Wenn Sie Hilfe brauchen, antworten Sie auf diese E-Mail.");
                templateData.put("footerText", "Automatische Bestaetigung des Anfrageeingangs von 3D-Fab.");
                yield "Wir haben Ihre Kontaktanfrage erhalten #" + requestId + " - 3D-Fab";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Demande de contact recue");
                templateData.put("headlineText", "Nous avons recu votre demande de contact");
                templateData.put("greetingText", "Bonjour " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Merci pour votre message. Notre equipe vous repondra des que possible.");
                templateData.put("requestIdHintText", "Veuillez conserver cet ID de demande pour vos futures references de commande :");
                templateData.put("detailsTitleText", "Details de la demande");
                templateData.put("labelRequestId", "ID de demande");
                templateData.put("labelDate", "Date");
                templateData.put("labelRequestType", "Type de demande");
                templateData.put("labelCustomerType", "Type de client");
                templateData.put("labelName", "Nom");
                templateData.put("labelCompany", "Entreprise");
                templateData.put("labelContactPerson", "Contact");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Telephone");
                templateData.put("labelMessage", "Message");
                templateData.put("labelAttachments", "Pieces jointes");
                templateData.put("supportText", "Si vous avez besoin d'aide, repondez a cet email.");
                templateData.put("footerText", "Confirmation automatique de reception de demande par 3D-Fab.");
                yield "Nous avons recu votre demande de contact #" + requestId + " - 3D-Fab";
            }
            default -> {
                templateData.put("emailTitle", "Richiesta di contatto ricevuta");
                templateData.put("headlineText", "Abbiamo ricevuto la tua richiesta di contatto");
                templateData.put("greetingText", "Ciao " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Grazie per averci contattato. Il nostro team ti rispondera' il prima possibile.");
                templateData.put("requestIdHintText", "Conserva questo ID richiesta per i futuri riferimenti d'ordine:");
                templateData.put("detailsTitleText", "Dettagli richiesta");
                templateData.put("labelRequestId", "ID richiesta");
                templateData.put("labelDate", "Data");
                templateData.put("labelRequestType", "Tipo richiesta");
                templateData.put("labelCustomerType", "Tipo cliente");
                templateData.put("labelName", "Nome");
                templateData.put("labelCompany", "Azienda");
                templateData.put("labelContactPerson", "Contatto");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Telefono");
                templateData.put("labelMessage", "Messaggio");
                templateData.put("labelAttachments", "Allegati");
                templateData.put("supportText", "Se hai bisogno, rispondi direttamente a questa email.");
                templateData.put("footerText", "Conferma automatica di ricezione richiesta da 3D-Fab.");
                yield "Abbiamo ricevuto la tua richiesta di contatto #" + requestId + " - 3D-Fab";
            }
        };
    }

    public String localizeRequestType(String requestType, String language) {
        if (requestType == null || requestType.isBlank()) {
            return "-";
        }

        String normalized = requestType.trim().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "en" -> switch (normalized) {
                case "custom", "print_service" -> "Custom part request";
                case "series" -> "Series production request";
                case "consult", "design_service" -> "Consultation request";
                case "question" -> "General question";
                default -> requestType;
            };
            case "de" -> switch (normalized) {
                case "custom", "print_service" -> "Anfrage fuer Einzelteil";
                case "series" -> "Anfrage fuer Serienproduktion";
                case "consult", "design_service" -> "Beratungsanfrage";
                case "question" -> "Allgemeine Frage";
                default -> requestType;
            };
            case "fr" -> switch (normalized) {
                case "custom", "print_service" -> "Demande de piece personnalisee";
                case "series" -> "Demande de production en serie";
                case "consult", "design_service" -> "Demande de conseil";
                case "question" -> "Question generale";
                default -> requestType;
            };
            default -> switch (normalized) {
                case "custom", "print_service" -> "Richiesta pezzo personalizzato";
                case "series" -> "Richiesta produzione in serie";
                case "consult", "design_service" -> "Richiesta consulenza";
                case "question" -> "Domanda generale";
                default -> requestType;
            };
        };
    }

    public String localizeCustomerType(String customerType, String language) {
        if (customerType == null || customerType.isBlank()) {
            return "-";
        }
        String normalized = customerType.trim().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "en" -> switch (normalized) {
                case "private" -> "Private";
                case "business" -> "Business";
                default -> customerType;
            };
            case "de" -> switch (normalized) {
                case "private" -> "Privat";
                case "business" -> "Unternehmen";
                default -> customerType;
            };
            case "fr" -> switch (normalized) {
                case "private" -> "Prive";
                case "business" -> "Entreprise";
                default -> customerType;
            };
            default -> switch (normalized) {
                case "private" -> "Privato";
                case "business" -> "Azienda";
                default -> customerType;
            };
        };
    }

    public Locale localeForLanguage(String language) {
        return switch (language) {
            case "en" -> Locale.ENGLISH;
            case "de" -> Locale.GERMAN;
            case "fr" -> Locale.FRENCH;
            default -> Locale.ITALIAN;
        };
    }

    public String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "it";
        }
        String normalized = language.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("en")) {
            return "en";
        }
        if (normalized.startsWith("de")) {
            return "de";
        }
        if (normalized.startsWith("fr")) {
            return "fr";
        }
        return "it";
    }

    public String resolveRecipientName(CustomQuoteRequest request, String language) {
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        if (request.getContactPerson() != null && !request.getContactPerson().isBlank()) {
            return request.getContactPerson().trim();
        }
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            return request.getCompanyName().trim();
        }
        return switch (language) {
            case "en" -> "customer";
            case "de" -> "Kunde";
            case "fr" -> "client";
            default -> "cliente";
        };
    }
}

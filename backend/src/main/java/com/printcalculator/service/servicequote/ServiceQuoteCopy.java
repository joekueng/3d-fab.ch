package com.printcalculator.service.servicequote;

import java.util.Map;
import java.util.LinkedHashMap;
import com.printcalculator.entity.ServiceQuoteRevision;

public final class ServiceQuoteCopy {
    private ServiceQuoteCopy() {}
    public static Map<String,String> labels(String language) {
        String[] keys = new String[]{"quote", "accepted", "preview", "description", "hourly", "lump", "estimate", "variation", "maximum", "validUntil", "acceptedAt", "customer", "scope", "termsVersion", "privacyVersion", "inviteSubject", "inviteIntro", "action", "receiptSubject", "receiptIntro", "adminSubject", "adminIntro", "footer", "declaration"};
        String[] values = switch (language) {
            case "it" -> new String[]{"Preventivo", "Preventivo accettato", "Bozza / anteprima", "Descrizione del lavoro", "Stima a ore", "Stima a corpo", "Importo stimato", "Variazione massima", "Importo massimo", "Valido fino al", "Accettato il", "Cliente", "Prestazioni e prodotti descritti, inclusi costi di preparazione. Spedizione esclusa.", "Versione termini", "Versione privacy", "Il tuo preventivo", "Apri il preventivo per leggere la proposta, inserire i tuoi dati e accettarla.", "Apri il preventivo", "Conferma accettazione preventivo", "La tua accettazione è stata registrata. Il PDF allegato contiene il preventivo accettato e i dati che hai dichiarato. Conservalo: il documento non sarà disponibile riaprendo il link.", "Preventivo accettato dal cliente", "È stata registrata una nuova accettazione. I dati e il documento sono disponibili nel back-office.", "3D Fab Küng Caletti", "Accetto il preventivo %s, revisione %d, per il lavoro descritto, con importo stimato CHF %s e variazione massima %s, fino a CHF %s."};
            case "en" -> new String[]{"Quote", "Accepted quote", "Draft / preview", "Scope of work", "Hourly estimate", "Lump sum estimate", "Estimated amount", "Maximum increase", "Maximum amount", "Valid until", "Accepted at", "Customer", "Described services and products, including setup. Shipping excluded.", "Terms version", "Privacy version", "Your quote", "Open the quote to review the proposal, enter your details and accept it.", "Open quote", "Quote acceptance confirmation", "Your acceptance has been recorded. The attached PDF contains the accepted quote and the details you provided. Keep it: reopening the link will not make the document available.", "Customer accepted a quote", "A new acceptance has been recorded. Details and the document are available in the back office.", "3D Fab Küng Caletti", "I accept quote %s, revision %d, for the described work, with an estimated amount of CHF %s and maximum increase of %s, up to CHF %s."};
            case "de" -> new String[]{"Offerte", "Angenommene Offerte", "Entwurf / Vorschau", "Leistungsbeschreibung", "Schätzung nach Stunden", "Pauschale Schätzung", "Geschätzter Betrag", "Maximale Erhöhung", "Höchstbetrag", "Gültig bis", "Angenommen am", "Kunde", "Beschriebene Leistungen und Produkte einschliesslich Einrichtung. Versand ausgeschlossen.", "AGB-Version", "Datenschutzversion", "Deine Offerte", "Öffne die Offerte, prüfe den Vorschlag, gib deine Daten ein und nimm ihn an.", "Offerte öffnen", "Bestätigung der Offertannahme", "Deine Annahme wurde gespeichert. Das beigefügte PDF enthält die angenommene Offerte und deine Angaben. Bewahre es auf: Beim erneuten Öffnen des Links ist das Dokument nicht verfügbar.", "Offerte vom Kunden angenommen", "Eine neue Annahme wurde gespeichert. Angaben und Dokument sind im Backoffice verfügbar.", "3D Fab Küng Caletti", "Ich nehme die Offerte %s, Revision %d, für die beschriebenen Arbeiten mit einem geschätzten Betrag von CHF %s und einer maximalen Erhöhung von %s bis zu CHF %s an."};
            case "fr" -> new String[]{"Devis", "Devis accepté", "Brouillon / aperçu", "Description du travail", "Estimation horaire", "Estimation forfaitaire", "Montant estimé", "Augmentation maximale", "Montant maximal", "Valable jusqu’au", "Accepté le", "Client", "Prestations et produits décrits, préparation incluse. Expédition exclue.", "Version des conditions", "Version de confidentialité", "Votre devis", "Ouvrez le devis pour consulter la proposition, saisir vos coordonnées et l’accepter.", "Ouvrir le devis", "Confirmation d’acceptation du devis", "Votre acceptation a été enregistrée. Le PDF joint contient le devis accepté et vos coordonnées déclarées. Conservez-le : le document ne sera pas disponible en rouvrant le lien.", "Devis accepté par le client", "Une nouvelle acceptation a été enregistrée. Les coordonnées et le document sont disponibles dans le back-office.", "3D Fab Küng Caletti", "J’accepte le devis %s, révision %d, pour le travail décrit, avec un montant estimé de CHF %s et une augmentation maximale de %s, jusqu’à CHF %s."};
            default -> throw new IllegalArgumentException("Unsupported quote language");
        };
        var result = new LinkedHashMap<String,String>();
        for (int i=0;i<keys.length;i++) result.put(keys[i],values[i]);
        return Map.copyOf(result);
    }
    public static String variation(ServiceQuoteRevision r) {
        return r.getContent().variationValue().toPlainString() +
            (r.getContent().variationType() == com.printcalculator.dto.ServiceQuoteContent.VariationType.PERCENTAGE ? "%" : " CHF");
    }
    public static String declaration(ServiceQuoteRevision r) {
        return labels(r.getContent().language()).get("declaration").formatted(r.getQuoteId(), r.getRevisionNumber(),
            r.getEstimatedAmountChf().toPlainString(), variation(r), r.getMaximumAmountChf().toPlainString());
    }
}

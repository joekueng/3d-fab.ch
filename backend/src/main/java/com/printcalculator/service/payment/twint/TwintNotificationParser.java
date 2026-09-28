package com.printcalculator.service.payment.twint;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Format from the Italian TWINT notification: labels and values on separate lines.
 * Input must be MIME-decoded and authenticated before reconciliation. */
@Component
public class TwintNotificationParser {
    public record Notification(String message, BigDecimal amount, String transactionId, OffsetDateTime transactionAt) {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu, HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final String SUCCESS = "La transazione è appena andata a buon fine!";

    public Optional<Notification> parse(String body) {
        List<String> lines = body.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        int success = lines.indexOf(SUCCESS);
        int amountLabel = lines.indexOf("Importo");
        int messageLabel = lines.indexOf("Messaggio");
        if (success < 0 || amountLabel <= success || messageLabel <= amountLabel) return Optional.empty();
        Map<String, String> fields = new HashMap<>();
        Set<String> labels = Set.of("Importo", "Messaggio", "Numero di transazione", "Data della transazione");
        for (int i = 0; i < lines.size(); i++) {
            String label = lines.get(i);
            if (!labels.contains(label)) continue;
            if (i + 1 >= lines.size() || labels.contains(lines.get(i + 1))
                    || fields.putIfAbsent(label, lines.get(i + 1)) != null) return Optional.empty();
        }
        try {
            int messageEnd = messageLabel + 1;
            while (messageEnd < lines.size()
                    && !lines.get(messageEnd).startsWith("La preghiamo di aggiungere ai suoi contatti")
                    && !lines.get(messageEnd).equals("Cordiali saluti")) messageEnd++;
            String message = String.join("\n", lines.subList(messageLabel + 1, messageEnd));
            String rawAmount = fields.getOrDefault("Importo", "").replaceFirst("^[A-Z]{3}\\s+", "");
            if (message.isBlank() || message.length() > 1000 || !rawAmount.matches("[0-9]{1,10}([.,][0-9]{1,2})?")) return Optional.empty();
            BigDecimal amount = new BigDecimal(rawAmount.replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY);
            String transaction = fields.get("Numero di transazione");
            if (transaction != null && !transaction.matches("[a-zA-Z0-9-]{1,255}")) return Optional.empty();
            if (transaction != null && transaction.matches("(?i)[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")) {
                transaction = transaction.toLowerCase(Locale.ROOT);
            }
            OffsetDateTime date = null;
            if (fields.containsKey("Data della transazione")) {
                LocalDateTime local = LocalDateTime.parse(fields.get("Data della transazione"), DATE);
                List<ZoneOffset> offsets = ZoneId.of("Europe/Zurich").getRules().getValidOffsets(local);
                if (offsets.size() == 1) date = local.atOffset(offsets.getFirst());
            }
            return Optional.of(new Notification(message, amount, transaction, date));
        } catch (DateTimeParseException | NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }
}

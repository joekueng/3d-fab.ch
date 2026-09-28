package com.printcalculator.service.payment.twint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TwintNotificationParserTest {
    final TwintNotificationParser parser = new TwintNotificationParser();
    static final String BODY = """
            Buongiorno Negozio di test

            La transazione è appena andata a buon fine!

            Importo
            CHF 23.90

            Data della transazione
            20.09.2026, 09:52

            Numero di transazione
            12345678-1234-1234-1234-123456789abc

            Nome del negozio
            Negozio di test

            Desidera annullare questa transazione? https://example.test/reversal?orderUuid=11111111-1111-1111-1111-111111111111

            Informazioni sul cliente
            Messaggio

            abcdef12-1234-1234-1234-123456789abc

            Cordiali saluti
            Il suo team TWINT
            """;
    @Test void parsesActualFieldLayoutWithoutConfusingOtherUuids() {
        var result = parser.parse(BODY).orElseThrow();
        assertEquals("abcdef12-1234-1234-1234-123456789abc", result.message());
        assertEquals(new BigDecimal("23.90"), result.amount());
        assertEquals("12345678-1234-1234-1234-123456789abc", result.transactionId());
        assertEquals(OffsetDateTime.parse("2026-09-20T09:52:00+02:00"), result.transactionAt());
    }
    @ParameterizedTest @ValueSource(strings = {"23.999", "-23.90", "23.90 EUR", "1,000.00", "NaN", "23.90 extra"})
    void rejectsUncertainAmounts(String amount) {
        assertTrue(parser.parse(BODY.replace("23.90", amount)).isEmpty());
    }
    @Test void rejectsFailureMissingMessageAndDuplicateFields() {
        assertTrue(parser.parse(BODY.replace("La transazione è appena andata a buon fine!", "La transazione è fallita.")).isEmpty());
        assertTrue(parser.parse(BODY.replace("Messaggio", "Altro campo")).isEmpty());
        assertTrue(parser.parse(BODY + "\nImporto\nCHF 42.00").isEmpty());
    }
    @Test void successPhraseInCustomerMessageIsInsufficient() {
        String body = BODY.replace("La transazione è appena andata a buon fine!", "")
                .replace("abcdef12-1234-1234-1234-123456789abc", "La transazione è appena andata a buon fine!");
        assertTrue(parser.parse(body).isEmpty());
    }
    @Test void optionalFieldsMayBeAbsent() {
        var result = parser.parse(BODY.replace("Numero di transazione\n12345678-1234-1234-1234-123456789abc", "")
                .replace("Data della transazione\n20.09.2026, 09:52", "")).orElseThrow();
        assertNull(result.transactionId()); assertNull(result.transactionAt());
    }

    @Test void decodesQuotedPrintableBeforeFindingFields() throws Exception {
        String encoded = BODY.replace("è", "=C3=A8")
                .replace("abcdef12-1234-1234-1234-123456789abc", "abcdef12-1234-1234-1234-=\n123456789abc")
                .replace("\n", "\r\n");
        String raw = "MIME-Version: 1.0\r\nContent-Type: multipart/alternative; boundary=test-boundary\r\n\r\n"
                + "--test-boundary\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n"
                + encoded + "\r\n--test-boundary\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n<p>Ignored alternate</p>\r\n--test-boundary--\r\n";
        var mime = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()),
                new java.io.ByteArrayInputStream(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var result = parser.parse(TwintMailboxReader.text(mime, 0)).orElseThrow();
        assertEquals("abcdef12-1234-1234-1234-123456789abc", result.message());
        assertEquals(new BigDecimal("23.90"), result.amount());
    }

    @Test void htmlOnlyAlternativeKeepsLabelsAndValuesSeparate() throws Exception {
        String html = "<p>La transazione è appena andata a buon fine!</p><table><tr><td><p>Importo</p><p>CHF 23.90</p></td></tr>"
                + "<tr><td><p>Messaggio</p><p>abcdef12</p></td></tr></table><p>Cordiali saluti</p>";
        var mime = new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        mime.setText(html, "UTF-8", "html");
        mime.saveChanges();
        assertEquals("abcdef12", parser.parse(TwintMailboxReader.text(mime, 0)).orElseThrow().message());
    }
}

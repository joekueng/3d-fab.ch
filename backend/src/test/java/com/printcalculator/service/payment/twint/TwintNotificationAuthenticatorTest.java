package com.printcalculator.service.payment.twint;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.james.jdkim.DKIMSigner;
import org.apache.james.jdkim.exceptions.TempFailException;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TwintNotificationAuthenticatorTest {
    static KeyPair key;
    TwintNotificationAuthenticator authenticator;
    static final String RECIPIENT = "merchant@example.test";
    static final String HEADERS = "From: no-reply@twintpay.ch\r\nTo: " + RECIPIENT
            + "\r\nSubject: Transazione di CHF 23.90 eseguita\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n";
    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); key = generator.generateKeyPair();
    }
    @BeforeEach void setup() {
        var config = new TwintProperties(); config.setOriginalRecipient(RECIPIENT);
        authenticator = new TwintNotificationAuthenticator(config, (method, selector, domain) ->
                List.of("v=DKIM1; k=rsa; p=" + Base64.getEncoder().encodeToString(key.getPublic().getEncoded())));
    }
    String signed(String extraTags) throws Exception {
        String message = HEADERS + "\r\n" + TwintNotificationParserTest.BODY.replace("\n", "\r\n");
        String signature = new DKIMSigner("v=1; a=rsa-sha256; c=relaxed/relaxed; d=twintpay.ch; s=test; h=from:to:subject:mime-version:content-type; " + extraTags,
                key.getPrivate()).sign(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
        return signature + "\r\n" + message;
    }
    MimeMessage mime(String raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void verifiesFullSignatureEvenWithForwardingHeaders() throws Exception {
        String raw = "Received: from gmail.example by mailbox.example; Wed, 23 Sep 2026 10:00:00 +0200\r\n"
                + "Authentication-Results: attacker.example; dkim=fail\r\n" + signed("");
        assertTrue(authenticator.isAuthentic(mime(raw)));
    }
    @Test void rejectsForgedPassHeadersWithoutValidSignature() throws Exception {
        assertFalse(authenticator.isAuthentic(mime("Authentication-Results: mx.google.com; dkim=pass header.d=twintpay.ch\r\n" + HEADERS + "\r\npaid")));
    }
    @Test void rejectsTamperedAmountRecipientAndDuplicateFrom() throws Exception {
        String raw = signed("");
        assertFalse(authenticator.isAuthentic(mime(raw.replace("23.90", "25.00"))));
        assertFalse(authenticator.isAuthentic(mime(raw.replace(RECIPIENT, "different@example.test"))));
        assertFalse(authenticator.isAuthentic(mime("From: no-reply@twintpay.ch\r\n" + raw)));
    }
    @Test void rejectsPartialBodySignatures() throws Exception {
        assertFalse(authenticator.isAuthentic(mime(signed("l=10;"))));
    }
    @Test void rejectsMessagesSignedForAnotherMerchant() throws Exception {
        var config = new TwintProperties(); config.setOriginalRecipient("other@example.test");
        var otherMerchant = new TwintNotificationAuthenticator(config, (m,s,d) -> List.of());
        assertFalse(otherMerchant.isAuthentic(mime(signed(""))));
    }
    @Test void dnsFailureIsRetryable() throws Exception {
        var config = new TwintProperties(); config.setOriginalRecipient(RECIPIENT);
        var offline = new TwintNotificationAuthenticator(config, (m,s,d) -> { throw new TempFailException("Test DNS unavailable"); });
        assertThrows(TempFailException.class, () -> offline.isAuthentic(mime(signed(""))));
    }
}

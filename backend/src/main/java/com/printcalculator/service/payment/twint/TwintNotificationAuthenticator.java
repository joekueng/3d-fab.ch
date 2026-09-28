package com.printcalculator.service.payment.twint;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.Headers;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.*;
import org.apache.james.jdkim.impl.DNSPublicKeyRecordRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.ExtendedResolver;
import java.io.*;
import java.time.Duration;
import java.util.*;

/** Verify the original TWINT signature, including the full MIME body and signed recipient.
 * Forwarding headers and Authentication-Results are never evidence of authenticity. */
@Component
public class TwintNotificationAuthenticator {
    private final PublicKeyRecordRetriever keys;
    private final TwintProperties config;
    private static final Set<String> SIGNED_HEADERS = Set.of("from", "to", "subject", "mime-version", "content-type");

    @Autowired
    public TwintNotificationAuthenticator(TwintProperties config) {
        this(config, dnsKeys());
    }

    TwintNotificationAuthenticator(TwintProperties config, PublicKeyRecordRetriever keys) {
        this.config = config;
        this.keys = keys;
    }

    private static PublicKeyRecordRetriever dnsKeys() {
        ExtendedResolver resolver = new ExtendedResolver();
        resolver.setTimeout(Duration.ofSeconds(5));
        resolver.setRetries(1);
        return new DNSPublicKeyRecordRetriever(resolver);
    }

    public boolean isCandidate(MimeMessage message) throws MessagingException {
        Address[] from = message.getFrom();
        return from != null && from.length == 1 && from[0] instanceof InternetAddress address
                && "no-reply@twintpay.ch".equalsIgnoreCase(address.getAddress());
    }

    public boolean isAuthentic(MimeMessage message) throws MessagingException, IOException, TempFailException {
        if (!isCandidate(message)) return false;
        for (String header : Set.of("from", "to", "subject", "mime-version", "content-type", "content-transfer-encoding")) {
            String[] values = message.getHeader(header);
            if ((SIGNED_HEADERS.contains(header) && values == null) || (values != null && values.length != 1)) return false;
        }
        Address[] to = message.getRecipients(jakarta.mail.Message.RecipientType.TO);
        if (to == null || to.length != 1 || !(to[0] instanceof InternetAddress address)
                || !config.getOriginalRecipient().equalsIgnoreCase(address.getAddress())) return false;
        String[] signatures = message.getHeader("DKIM-Signature");
        if (signatures == null || signatures.length > 5) return false;
        List<String> fields = Collections.list(message.getAllHeaderLines());
        Headers headers = new Headers() {
            public List<String> getFields() { return fields; }
            public List<String> getFields(String name) {
                return fields.stream().filter(f -> f.regionMatches(true, 0, name + ":", 0, name.length() + 1)).toList();
            }
        };
        DKIMVerifier verifier = new DKIMVerifier((method, selector, domain) -> {
            // Never perform DNS lookups for arbitrary attacker-supplied domains/selectors.
            if (!"twintpay.ch".equalsIgnoreCase(domain.toString())
                    || !selector.toString().matches("[A-Za-z0-9_-]{1,63}")) throw new PermFailException("Untrusted signing domain or selector");
            return keys.getRecords(method, selector, domain);
        });
        try (InputStream body = message.getRawInputStream()) {
            byte[] raw = body.readNBytes(1_000_001);
            if (raw.length > 1_000_000) return false;
            List<SignatureRecord> verified = verifier.verify(headers, new ByteArrayInputStream(raw));
            boolean transferEncoding = message.getHeader("Content-Transfer-Encoding") != null;
            return verified != null && verified.stream().anyMatch(s -> acceptable(s, transferEncoding));
        } catch (TempFailException e) {
            throw e; // Retry temporary DNS failures without advancing the mailbox cursor.
        } catch (CompositeFailException e) {
            for (FailException cause : e.getExceptions()) if (cause instanceof TempFailException temporary) throw temporary;
            return false;
        } catch (FailException | IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    private boolean acceptable(SignatureRecord signature, boolean transferEncoding) {
        Set<String> signed = new HashSet<>();
        signature.getHeaders().forEach(h -> signed.add(h.toString().toLowerCase(Locale.ROOT)));
        return "twintpay.ch".equalsIgnoreCase(signature.getDToken().toString())
                && "rsa".equalsIgnoreCase(signature.getHashKeyType().toString())
                && "sha256".equalsIgnoreCase(signature.getHashMethod().toString())
                && (!transferEncoding || signed.contains("content-transfer-encoding"))
                && signature.getBodyHashLimit() < 0 // Never allow unsigned content appended after l=.
                && signed.containsAll(SIGNED_HEADERS);
    }
}

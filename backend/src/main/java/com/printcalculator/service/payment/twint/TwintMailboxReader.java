package com.printcalculator.service.payment.twint;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwintMailboxReader {
    private final TwintProperties config;
    private final OrderRepository orders;
    private final TwintMailboxCursorRepository cursors;
    private final TwintReceiptRepository receipts;
    private final TwintNotificationAuthenticator authenticator;
    private final TwintNotificationParser parser;
    private final TwintReconciliationService reconciliation;
    private final AtomicBoolean connectionLogged = new AtomicBoolean();

    @Transactional
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("TWINT inbox reader disabled (TWINT_INBOX_ENABLED=false)");
            return;
        }
        if (config.getInitialSince() == null || config.getPassword() == null || config.getPassword().isBlank()
                || config.getActiveWindow().isNegative() || config.getActiveWindow().isZero()
                || config.getPollMs() <= 0 || config.getPeriodicInterval().isNegative() || config.getPeriodicInterval().isZero()
                || config.getBatchSize() < 1 || config.getBatchSize() > 500) {
            throw new IllegalStateException("Configure TWINT IMAP credentials, initial-since, positive window/intervals and batch-size 1..500");
        }
        if (!cursors.existsById(config.mailboxKey())) {
            var cursor = new TwintMailboxCursor();
            cursor.setId(config.mailboxKey());
            cursor.setInitialSince(config.getInitialSince());
            cursors.saveAndFlush(cursor);
            log.info("TWINT inbox reader initialized: folder={}, initialSince={}, activeWindow={}",
                    config.getFolder(), config.getInitialSince(), config.getActiveWindow());
        } else {
            log.info("TWINT inbox reader resumed: folder={}, saved cursor and initial cutoff retained", config.getFolder());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void poll() throws Exception {
        if (!config.isEnabled()) return;
        TwintMailboxCursor cursor = cursors.findLockedById(config.mailboxKey()).orElseThrow();
        Properties properties = new Properties();
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imaps.connectiontimeout", "5000");
        properties.setProperty("mail.imaps.timeout", "5000");
        properties.setProperty("mail.imaps.writetimeout", "5000");
        properties.setProperty("mail.imaps.peek", "true");
        log.debug("TWINT inbox connecting via IMAPS: host={}, port={}, folder={}",
                config.getHost(), config.getPort(), config.getFolder());
        try (Store store = Session.getInstance(properties).getStore("imaps")) {
            store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
            if (connectionLogged.compareAndSet(false, true)) {
                log.info("TWINT inbox IMAPS connection established: host={}, folder={}",
                        config.getHost(), config.getFolder());
            }
            Folder folder = store.getFolder(config.getFolder());
            try {
                folder.open(Folder.READ_ONLY);
                UIDFolder uids = (UIDFolder) folder;
                long validity = uids.getUIDValidity();
                if (cursor.getUidValidity() != validity) {
                    // UIDVALIDITY changes invalidate UIDs. Replay from the persisted cutoff;
                    // transaction IDs still prevent a second confirmation.
                    cursor.setUidValidity(validity);
                    cursor.setLastUid(0);
                    log.info("TWINT inbox UID validity changed; scanning again from saved initial cutoff {}",
                            cursor.getInitialSince());
                }
                if (cursor.getLastUid() == 0) {
                    Message[] eligible = folder.search(new ReceivedDateTerm(ComparisonTerm.GE,
                            Date.from(cursor.getInitialSince().toInstant())));
                    long first = Long.MAX_VALUE;
                    for (Message message : eligible) first = Math.min(first, uids.getUID(message));
                    if (first == Long.MAX_VALUE) {
                        if (folder.getMessageCount() > 0) cursor.setLastUid(uids.getUID(folder.getMessage(folder.getMessageCount())));
                        log.info("TWINT inbox opened: no messages received since saved cutoff {}; mailbox count={}, cursorUid={}",
                                cursor.getInitialSince(), folder.getMessageCount(), cursor.getLastUid());
                        return;
                    }
                    cursor.setLastUid(first - 1);
                }
                long last = folder.getMessageCount() == 0 ? 0 : uids.getUID(folder.getMessage(folder.getMessageCount()));
                long end = Math.min(last, cursor.getLastUid() + config.getBatchSize());
                if (end <= cursor.getLastUid()) {
                    log.debug("TWINT inbox has no new UIDs after {}", cursor.getLastUid());
                    return;
                }
                long start = cursor.getLastUid() + 1;
                int olderThanCutoff = 0;
                int alreadyRecorded = 0;
                int otherSender = 0;
                int candidateCount = 0;
                for (Message message : uids.getMessagesByUID(cursor.getLastUid() + 1, end)) {
                    if (message == null) continue;
                    long uid = uids.getUID(message);
                    Date received = message.getReceivedDate();
                    if (received == null || received.toInstant().isBefore(cursor.getInitialSince().toInstant())) {
                        olderThanCutoff++;
                        continue;
                    }
                    if (receipts.existsByMailboxKeyAndUidValidityAndMessageUid(config.mailboxKey(), validity, uid)) {
                        alreadyRecorded++;
                        continue;
                    }
                    if (!authenticator.isCandidate((MimeMessage) message)) {
                        otherSender++;
                        continue;
                    }
                    candidateCount++;
                    TwintReceipt receipt = new TwintReceipt();
                    receipt.setMailboxKey(config.mailboxKey());
                    receipt.setUidValidity(validity);
                    receipt.setMessageUid(uid);
                    receipt.setContentHash(hash(config.mailboxKey() + ":" + validity + ":" + uid));
                    if (message.getSize() > 1_000_000) {
                        receipt.setOutcome("MESSAGE_TOO_LARGE");
                    } else if (!authenticator.isAuthentic((MimeMessage) message)) {
                        receipt.setOutcome("AUTHENTICITY_REVIEW");
                    } else {
                        Optional<TwintNotificationParser.Notification> notification;
                        try {
                            String body = text(message, 0);
                            receipt.setContentHash(hash(body));
                            notification = parser.parse(body);
                        } catch (IllegalArgumentException | jakarta.mail.internet.ParseException malformed) {
                            notification = Optional.empty();
                        }
                        if (notification.isEmpty()) receipt.setOutcome("FORMAT_REVIEW");
                        else reconciliation.reconcile(receipt, notification.get());
                    }
                    receipts.save(receipt);
                    log.info("TWINT inbox candidate uid={} outcome={} matchedOrder={}",
                            uid, receipt.getOutcome(), receipt.getOrderId());
                }
                cursor.setLastUid(end);
                log.info("TWINT inbox scanned UIDs {}..{}: candidates={}, otherSender={}, olderThanCutoff={}, alreadyRecorded={}",
                        start, end, candidateCount, otherSender, olderThanCutoff, alreadyRecorded);
            } finally {
                if (folder.isOpen()) folder.close(false);
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean hasActivePaymentWindow() {
        return orders.hasActivePaymentWindow(OffsetDateTime.now().minus(config.getActiveWindow()));
    }

    static String text(Part part, int depth) throws Exception {
        if (depth > 10) throw new IllegalArgumentException("MIME nesting limit exceeded");
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) return "";
        if (part.isMimeType("text/plain")) return bounded(part.getContent().toString());
        if (part.isMimeType("text/html")) {
            var html = Jsoup.parse(bounded(part.getContent().toString()));
            html.select("br").append("\\n");
            html.select("tr,p,div").append("\\n");
            return html.wholeText().replace("\\n", "\n");
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            if (part.isMimeType("multipart/alternative")) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    if (multipart.getBodyPart(i).isMimeType("text/plain")) return text(multipart.getBodyPart(i), depth + 1);
                }
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) result.append(text(multipart.getBodyPart(i), depth + 1)).append('\n');
            return bounded(result.toString());
        }
        return "";
    }

    private static String bounded(String value) throws MessagingException {
        if (value.length() > 1_000_000) throw new IllegalArgumentException("MIME content limit exceeded");
        return value;
    }

    private static String hash(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}

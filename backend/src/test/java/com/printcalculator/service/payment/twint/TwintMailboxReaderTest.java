package com.printcalculator.service.payment.twint;

import com.printcalculator.entity.*;
import com.printcalculator.repository.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwintMailboxReaderTest {
    @Mock OrderRepository orders;
    @Mock TwintMailboxCursorRepository cursors;
    @Mock TwintReceiptRepository receipts;
    @Mock TwintNotificationAuthenticator auth;
    @Mock TwintNotificationParser parser;
    @Mock TwintReconciliationService reconciliation;
    TwintProperties config;
    TwintMailboxReader reader;
    TwintMailboxCursor cursor;
    @BeforeEach void setup() {
        config = new TwintProperties(); config.setEnabled(true); config.setPassword("fixture-secret");
        config.setInitialSince(OffsetDateTime.now().minusDays(1));
        reader = new TwintMailboxReader(config, orders, cursors, receipts, auth, parser, reconciliation);
        cursor = new TwintMailboxCursor(); cursor.setId(config.mailboxKey()); cursor.setInitialSince(config.getInitialSince());
        cursor.setUidValidity(7); cursor.setLastUid(100);
    }
    @Test void windowLookupDoesNotOpenMailbox() {
        assertFalse(reader.hasActivePaymentWindow());
        verify(orders).hasActivePaymentWindow(any());
        verifyNoInteractions(cursors, receipts, auth);
    }
    @Test void disabledAutomationNeverQueriesOrdersOrMailbox() throws Exception {
        config.setEnabled(false); reader.poll(); verifyNoInteractions(orders, cursors, receipts, auth);
    }
    @Test void initializationRequiresExplicitCutoff() {
        config.setInitialSince(null);
        assertThrows(IllegalStateException.class, reader::initialize);
        verifyNoInteractions(cursors);
    }
    @Test void savedCursorSurvivesInitialization() {
        when(cursors.existsById(config.mailboxKey())).thenReturn(true);
        reader.initialize(); verify(cursors, never()).saveAndFlush(any());
    }
    @Test void periodicReadWithoutActiveOrdersAdvancesCursorWithoutDeleting() throws Exception {
        when(cursors.findLockedById(config.mailboxKey())).thenReturn(Optional.of(cursor));
        Session session = mock(Session.class); Store store = mock(Store.class);
        Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));
        UIDFolder uids = (UIDFolder) folder;
        MimeMessage message = mock(MimeMessage.class);
        when(session.getStore("imaps")).thenReturn(store); when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.isOpen()).thenReturn(true); when(folder.getMessageCount()).thenReturn(1);
        when(folder.getMessage(1)).thenReturn(message); when(uids.getUIDValidity()).thenReturn(7L);
        when(uids.getUID(message)).thenReturn(101L); when(uids.getMessagesByUID(101, 101)).thenReturn(new Message[]{message});
        when(message.getReceivedDate()).thenReturn(new Date());
        when(auth.isCandidate(message)).thenReturn(true);
        // Unverified messages are retained for manual review, never paid.
        try (var sessions = mockStatic(Session.class)) {
            sessions.when(() -> Session.getInstance(any(Properties.class))).thenReturn(session);
            reader.poll();
        }
        assertEquals(101, cursor.getLastUid());
        var receipt = ArgumentCaptor.forClass(TwintReceipt.class); verify(receipts).save(receipt.capture());
        assertEquals("AUTHENTICITY_REVIEW", receipt.getValue().getOutcome());
        verify(folder).open(Folder.READ_ONLY); verify(folder).close(false);
        verify(message, never()).isSet(any());
        verifyNoInteractions(reconciliation, orders);
    }
}

package com.printcalculator.service.payment.twint;

import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import org.eclipse.angus.mail.imap.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwintIdleListenerTest {
    TwintMailboxReader reader;
    TwintProperties config;
    TwintIdleListener listener;
    IMAPStore store;
    IMAPFolder folder;
    IdleManager manager;
    CountDownLatch storeClosed;

    @BeforeEach void setup() throws Exception {
        reader = mock(TwintMailboxReader.class);
        config = new TwintProperties();
        config.setEnabled(true);
        listener = spy(new TwintIdleListener(reader, config));
        Session session = mock(Session.class);
        store = mock(IMAPStore.class);
        storeClosed = new CountDownLatch(1);
        doAnswer(call -> { storeClosed.countDown(); return null; }).when(store).close();
        folder = mock(IMAPFolder.class);
        manager = mock(IdleManager.class);
        doReturn(session).when(listener).createSession();
        doReturn(manager).when(listener).createManager(session);
        when(session.getStore("imaps")).thenReturn(store);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(store.hasCapability("IDLE")).thenReturn(true);
        when(folder.isOpen()).thenReturn(true);
    }

    @AfterEach void stop() { listener.stop(); }

    @Test void waitsWithoutPollingThenProcessesNotificationOnSameConnection() throws Exception {
        var watching = new CountDownLatch(1);
        doAnswer(call -> { watching.countDown(); return null; }).when(manager).watch(folder);
        listener.start();
        assertTrue(watching.await(3, TimeUnit.SECONDS));
        verify(reader, times(1)).readBatch(folder);
        var callbacks = ArgumentCaptor.forClass(MessageCountListener.class);
        verify(folder).addMessageCountListener(callbacks.capture());
        // Backlog is drained in separate batches before re-arming IDLE.
        when(reader.readBatch(folder)).thenReturn(true, false);
        callbacks.getValue().messagesAdded(new MessageCountEvent(folder, MessageCountEvent.ADDED, false, new Message[0]));
        verify(manager, timeout(3000).times(2)).watch(folder);
        verify(reader, times(3)).readBatch(folder);
        verify(store, times(1)).connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
        verify(reader, never()).poll();
        verify(reader, never()).hasActivePaymentWindow();
        listener.stop();
        verify(folder, atLeastOnce()).forceClose();
        assertTrue(storeClosed.await(3, TimeUnit.SECONDS));
    }

    @Test void notificationDuringReadIsNotLost() throws Exception {
        var callbacks = ArgumentCaptor.forClass(MessageCountListener.class);
        doAnswer(call -> {
            verify(folder).addMessageCountListener(callbacks.capture());
            callbacks.getValue().messagesAdded(new MessageCountEvent(folder, MessageCountEvent.ADDED, false, new Message[0]));
            return false;
        }).doReturn(false).when(reader).readBatch(folder);
        listener.start();
        verify(manager, timeout(3000).times(2)).watch(folder);
        verify(reader, times(2)).readBatch(folder);
    }

    @Test void unavailableIdleFallsBackWithoutStartingLegacyPoller() throws Exception {
        when(store.hasCapability("IDLE")).thenReturn(false);
        listener.start();
        verify(reader, timeout(3000)).readBatch(folder);
        verify(manager, never()).watch(any());
        verify(reader, never()).poll();
    }

    @Test void readFailureClosesConnectionAndBacksOff() throws Exception {
        when(reader.readBatch(folder)).thenThrow(new MessagingException("synthetic failure"));
        listener.start();
        assertTrue(storeClosed.await(3, TimeUnit.SECONDS));
        verify(reader, times(1)).readBatch(folder);
        verify(manager, never()).watch(any());
    }

    @Test void disabledDoesNotConnect() {
        config.setEnabled(false);
        listener.start();
        verifyNoInteractions(store);
    }

    @Test void secureReadOnlyConnectionSettings() {
        var properties = TwintIdleListener.connectionProperties();
        assertEquals("true", properties.getProperty("mail.imaps.ssl.checkserveridentity"));
        assertEquals("true", properties.getProperty("mail.imaps.peek"));
        assertEquals("true", properties.getProperty("mail.imaps.usesocketchannels"));
    }
}

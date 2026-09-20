package com.printcalculator.service.quote;

import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSessionCalculationServiceTest {
    @Mock
    private QuoteSessionRepository sessionRepository;
    @Mock
    private QuoteLineItemRepository lineItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private QuoteStorageService storageService;
    @Mock
    private QuoteSessionExpiryPolicy expiryPolicy;
    @InjectMocks
    private QuoteSessionCalculationService service;

    @Test
    void prepareForRecalculation_reusesActiveUnconvertedPrintSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = activePrintSession(sessionId);
        OffsetDateTime renewedExpiry = OffsetDateTime.now().plusMonths(3);
        when(sessionRepository.findLockedById(sessionId)).thenReturn(Optional.of(session));
        when(expiryPolicy.newExpiry()).thenReturn(renewedExpiry);
        when(sessionRepository.save(session)).thenReturn(session);

        Optional<QuoteSession> result = service.prepareForRecalculation(sessionId);

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.orElseThrow().getId());
        assertEquals(renewedExpiry, session.getExpiresAt());
        verify(lineItemRepository).deleteByQuoteSession_Id(sessionId);
        verify(storageService).clearSessionStorage(sessionId);
    }

    @Test
    void prepareForRecalculation_rejectsSessionAlreadyLinkedToOrder() throws Exception {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = activePrintSession(sessionId);
        when(sessionRepository.findLockedById(sessionId)).thenReturn(Optional.of(session));
        when(orderRepository.existsBySourceQuoteSession_Id(sessionId)).thenReturn(true);

        assertTrue(service.prepareForRecalculation(sessionId).isEmpty());

        verify(lineItemRepository, never()).deleteByQuoteSession_Id(sessionId);
        verify(storageService, never()).clearSessionStorage(sessionId);
    }

    @Test
    void prepareForRecalculation_rejectsConvertedSession() {
        UUID sessionId = UUID.randomUUID();
        QuoteSession session = activePrintSession(sessionId);
        session.setStatus("CONVERTED");
        when(sessionRepository.findLockedById(sessionId)).thenReturn(Optional.of(session));

        assertTrue(service.prepareForRecalculation(sessionId).isEmpty());

        verify(lineItemRepository, never()).deleteByQuoteSession_Id(sessionId);
    }

    private QuoteSession activePrintSession(UUID id) {
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus("ACTIVE");
        session.setSessionType("PRINT_QUOTE");
        return session;
    }
}

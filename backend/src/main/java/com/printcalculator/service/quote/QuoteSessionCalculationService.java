package com.printcalculator.service.quote;

import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteSessionExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
public class QuoteSessionCalculationService {
    private static final Logger logger = LoggerFactory.getLogger(QuoteSessionCalculationService.class);

    private final QuoteSessionRepository sessionRepository;
    private final QuoteLineItemRepository lineItemRepository;
    private final OrderRepository orderRepository;
    private final QuoteStorageService storageService;
    private final QuoteSessionExpiryPolicy expiryPolicy;

    public QuoteSessionCalculationService(QuoteSessionRepository sessionRepository,
                                          QuoteLineItemRepository lineItemRepository,
                                          OrderRepository orderRepository,
                                          QuoteStorageService storageService,
                                          QuoteSessionExpiryPolicy expiryPolicy) {
        this.sessionRepository = sessionRepository;
        this.lineItemRepository = lineItemRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
        this.expiryPolicy = expiryPolicy;
    }

    @Transactional
    public Optional<QuoteSession> prepareForRecalculation(UUID sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }

        Optional<QuoteSession> candidate = sessionRepository.findLockedById(sessionId);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        QuoteSession session = candidate.get();
        if (!"ACTIVE".equals(session.getStatus())
                || !"PRINT_QUOTE".equals(session.getSessionType())
                || session.getConvertedOrderId() != null
                || orderRepository.existsBySourceQuoteSession_Id(sessionId)) {
            return Optional.empty();
        }

        lineItemRepository.deleteByQuoteSession_Id(sessionId);
        try {
            storageService.clearSessionStorage(sessionId);
        } catch (IOException exception) {
            logger.warn("Could not fully clear stored files before recalculating quote session {}", sessionId, exception);
        }

        session.setExpiresAt(expiryPolicy.newExpiry());
        return Optional.of(sessionRepository.save(session));
    }
}

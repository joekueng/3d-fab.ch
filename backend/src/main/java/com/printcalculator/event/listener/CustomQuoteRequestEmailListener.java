package com.printcalculator.event.listener;

import com.printcalculator.event.CustomQuoteRequestCreatedEvent;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.service.request.CustomQuoteRequestNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomQuoteRequestEmailListener {

    private final CustomQuoteRequestRepository requestRepository;
    private final CustomQuoteRequestNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CustomQuoteRequestCreatedEvent event) {
        try {
            var request = requestRepository.findById(event.requestId())
                    .orElseThrow(() -> new IllegalStateException("Committed contact request not found"));
            notificationService.sendNotifications(request, event.attachmentsCount(), event.language());
        } catch (Exception ex) {
            log.error("Failed to process email notifications for contact request {}", event.requestId(), ex);
        }
    }
}

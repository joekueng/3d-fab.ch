package com.printcalculator.repository;

import com.printcalculator.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
    List<EmailLog> findByOrder_IdOrderByAttemptedAtDesc(UUID orderId);

    List<EmailLog> findByContactRequest_IdOrderByAttemptedAtDesc(UUID contactRequestId);

    boolean existsByEventTypeAndRecipientIgnoreCaseAndStatusIn(
            String eventType, String recipient, List<String> statuses);

    long deleteByAttemptedAtBefore(OffsetDateTime cutoff);
}

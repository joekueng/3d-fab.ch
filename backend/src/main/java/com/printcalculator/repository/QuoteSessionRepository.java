package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteSessionRepository extends JpaRepository<QuoteSession, UUID> {
    List<QuoteSession> findByCreatedAtBefore(java.time.OffsetDateTime cutoff);

    List<QuoteSession> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    Optional<QuoteSession> findByIdAndSessionType(UUID id, String sessionType);
}

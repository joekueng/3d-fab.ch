package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuoteSessionRepository extends JpaRepository<QuoteSession, UUID> {
    List<QuoteSession> findByCreatedAtBefore(java.time.OffsetDateTime cutoff);
}
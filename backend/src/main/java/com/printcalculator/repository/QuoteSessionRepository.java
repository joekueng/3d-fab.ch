package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteSessionRepository extends JpaRepository<QuoteSession, UUID> {
}
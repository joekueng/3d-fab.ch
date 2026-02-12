package com.printcalculator.repository;

import com.printcalculator.entity.CustomQuoteRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomQuoteRequestRepository extends JpaRepository<CustomQuoteRequest, UUID> {
}
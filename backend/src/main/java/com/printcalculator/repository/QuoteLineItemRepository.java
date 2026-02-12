package com.printcalculator.repository;

import com.printcalculator.entity.QuoteLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteLineItemRepository extends JpaRepository<QuoteLineItem, UUID> {
}
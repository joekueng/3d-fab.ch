package com.printcalculator.repository;

import com.printcalculator.entity.QuoteLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuoteLineItemRepository extends JpaRepository<QuoteLineItem, UUID> {
    List<QuoteLineItem> findByQuoteSessionId(UUID quoteSessionId);
    boolean existsByFilamentVariant_Id(Long filamentVariantId);
}

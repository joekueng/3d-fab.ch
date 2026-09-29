package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSessionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteSessionAttachmentRepository extends JpaRepository<QuoteSessionAttachment, UUID> {
    List<QuoteSessionAttachment> findByQuoteSessionIdOrderByCreatedAtAsc(UUID quoteSessionId);

    long countByQuoteSessionId(UUID quoteSessionId);

    Optional<QuoteSessionAttachment> findByIdAndQuoteSession_Id(UUID attachmentId, UUID quoteSessionId);
}

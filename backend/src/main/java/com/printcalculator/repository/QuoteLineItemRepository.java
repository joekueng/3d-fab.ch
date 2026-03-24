package com.printcalculator.repository;

import com.printcalculator.entity.QuoteLineItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteLineItemRepository extends JpaRepository<QuoteLineItem, UUID> {
    @EntityGraph(attributePaths = {"filamentVariant", "shopProduct", "shopProductVariant"})
    List<QuoteLineItem> findByQuoteSessionId(UUID quoteSessionId);

    @EntityGraph(attributePaths = {"filamentVariant", "shopProduct", "shopProductVariant"})
    List<QuoteLineItem> findByQuoteSessionIdOrderByCreatedAtAsc(UUID quoteSessionId);

    @EntityGraph(attributePaths = {"filamentVariant", "shopProduct", "shopProductVariant"})
    Optional<QuoteLineItem> findByIdAndQuoteSession_Id(UUID lineItemId, UUID quoteSessionId);

    @EntityGraph(attributePaths = {"shopProductVariant"})
    Optional<QuoteLineItem> findFirstByQuoteSession_IdAndLineItemTypeAndShopProductVariant_Id(
            UUID quoteSessionId,
            String lineItemType,
            UUID shopProductVariantId
    );
    boolean existsByFilamentVariant_Id(Long filamentVariantId);
    boolean existsByShopProduct_Id(UUID shopProductId);
    boolean existsByShopProductVariant_Id(UUID shopProductVariantId);
}

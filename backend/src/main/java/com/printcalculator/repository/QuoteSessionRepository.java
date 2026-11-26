package com.printcalculator.repository;

import com.printcalculator.entity.QuoteSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteSessionRepository extends JpaRepository<QuoteSession, UUID> {
    String HAS_PAID_ORDER = """
            exists (
                select o.id from Order o
                where o.sourceQuoteSession = s and
            """ + OrderRepository.PAID_NON_CANCELLED + ")";

    boolean existsByCalculatorSession_Id(UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from QuoteSession s where s.id = :id")
    Optional<QuoteSession> findLockedById(@Param("id") UUID id);

    List<QuoteSession> findByExpiresAtBefore(java.time.OffsetDateTime cutoff);

    List<QuoteSession> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    Optional<QuoteSession> findByIdAndSessionType(UUID id, String sessionType);

    @Query("select count(s) from QuoteSession s where s.status <> 'CAD_DELETED'")
    long countAllForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.status <> 'CAD_DELETED'
              and exists (
                select i.id from QuoteLineItem i where i.quoteSession = s
            )
            """)
    long countWithItemsForStatistics();

    @Query("select count(i) from QuoteLineItem i where i.quoteSession.status <> 'CAD_DELETED'")
    long countLineItemsForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.status <> 'CAD_DELETED'
              and exists (
                select o.id from Order o where o.sourceQuoteSession = s
            )
            """)
    long countConvertedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.status <> 'CAD_DELETED'
              and exists (
                select i.id from QuoteLineItem i where i.quoteSession = s
              )
              and
            """ + HAS_PAID_ORDER)
    long countPaidConvertedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.status <> 'CAD_DELETED'
              and exists (
                select i.id from QuoteLineItem i
                where i.quoteSession = s and i.updatedAt > i.createdAt
            )
            """)
    long countModifiedForStatistics();

    @Query("""
            select count(s) from QuoteSession s
            where s.status <> 'CAD_DELETED'
              and s.expiresAt < :now
              and exists (
                  select i.id from QuoteLineItem i where i.quoteSession = s
              )
              and not
            """ + HAS_PAID_ORDER)
    long countExpiredWithoutPaidConversionForStatistics(@Param("now") OffsetDateTime now);
}

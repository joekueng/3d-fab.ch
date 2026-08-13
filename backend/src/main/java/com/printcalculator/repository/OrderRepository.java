package com.printcalculator.repository;

import com.printcalculator.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findAllByOrderByCreatedAtDesc();

    boolean existsBySourceQuoteSession_Id(UUID sourceQuoteSessionId);

    @Query("""
            select count(o)
            from Order o
            where o.status <> 'CANCELLED'
              and exists (
                  select p.id from Payment p
                  where p.order = o and p.status = 'COMPLETED'
              )
            """)
    long countPaidNonCancelledForStatistics();

    @Query("""
            select coalesce(sum(o.totalChf), 0)
            from Order o
            where o.status <> 'CANCELLED'
              and exists (
                  select p.id from Payment p
                  where p.order = o and p.status = 'COMPLETED'
              )
            """)
    BigDecimal sumPaidNonCancelledTotalsForStatistics();

    @Query("""
            select coalesce(avg(o.totalChf), 0)
            from Order o
            where o.status <> 'CANCELLED'
              and exists (
                  select p.id from Payment p
                  where p.order = o and p.status = 'COMPLETED'
              )
            """)
    Double averagePaidNonCancelledTotalsForStatistics();

    @Query("""
            select count(distinct lower(o.customerEmail))
            from Order o
            where o.status <> 'CANCELLED'
              and exists (
                  select p.id from Payment p
                  where p.order = o and p.status = 'COMPLETED'
              )
            """)
    long countUniquePaidNonCancelledCustomersForStatistics();
}

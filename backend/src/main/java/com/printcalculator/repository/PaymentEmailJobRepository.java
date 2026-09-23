package com.printcalculator.repository;

import com.printcalculator.entity.PaymentEmailJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface PaymentEmailJobRepository extends JpaRepository<PaymentEmailJob, UUID> {
    boolean existsByOrderIdAndKind(UUID orderId, String kind);
    List<PaymentEmailJob> findByStatusAndDueAtLessThanEqualOrderByDueAtAsc(String status, OffsetDateTime now, Pageable page);
    List<PaymentEmailJob> findByStatusAndAttemptedAtBefore(String status, OffsetDateTime before, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PaymentEmailJob j where j.id = :id")
    Optional<PaymentEmailJob> findLockedById(UUID id);
    @Modifying
    @Query("update PaymentEmailJob j set j.status = 'CANCELLED' where j.orderId = :orderId and j.kind = 'REPORTED' and j.status = 'PENDING'")
    void cancelReported(UUID orderId);
}

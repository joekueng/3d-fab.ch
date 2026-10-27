package com.printcalculator.repository;
import com.printcalculator.entity.ServiceQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ServiceQuoteRepository extends JpaRepository<ServiceQuote, UUID> {
    List<ServiceQuote> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from ServiceQuote q where q.id=:id")
    Optional<ServiceQuote> lockById(UUID id);
}

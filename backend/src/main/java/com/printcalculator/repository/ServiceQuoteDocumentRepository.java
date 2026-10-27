package com.printcalculator.repository;
import com.printcalculator.entity.ServiceQuoteDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ServiceQuoteDocumentRepository extends JpaRepository<ServiceQuoteDocument,UUID> {
    Optional<ServiceQuoteDocument> findByAcceptanceId(UUID acceptanceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from ServiceQuoteDocument d where d.acceptanceId=:id")
    Optional<ServiceQuoteDocument> lockByAcceptanceId(UUID id);
    @Query("select d from ServiceQuoteDocument d where d.status='PENDING' or (d.status='READY' and (d.emailStatus='PENDING' or d.adminEmailStatus='PENDING')) order by d.id")
    List<ServiceQuoteDocument> findPending(org.springframework.data.domain.Pageable pageable);
}

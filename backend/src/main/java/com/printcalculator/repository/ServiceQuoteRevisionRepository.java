package com.printcalculator.repository;
import com.printcalculator.entity.ServiceQuoteRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ServiceQuoteRevisionRepository extends JpaRepository<ServiceQuoteRevision,UUID> {
    List<ServiceQuoteRevision> findByQuoteIdOrderByRevisionNumberDesc(UUID quoteId);
}

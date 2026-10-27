package com.printcalculator.repository;
import com.printcalculator.entity.ServiceQuoteAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ServiceQuoteAcceptanceRepository extends JpaRepository<ServiceQuoteAcceptance,UUID> {
    Optional<ServiceQuoteAcceptance> findByRevisionId(UUID revisionId);
}

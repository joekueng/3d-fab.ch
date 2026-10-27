package com.printcalculator.repository;
import com.printcalculator.entity.ServiceQuotePublicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ServiceQuotePublicLinkRepository extends JpaRepository<ServiceQuotePublicLink,UUID> {
    Optional<ServiceQuotePublicLink> findByRevisionId(UUID revisionId);
}

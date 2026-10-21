package com.printcalculator.repository;

import com.printcalculator.entity.LegalConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegalConsentRepository extends JpaRepository<LegalConsent, UUID> {
    Optional<LegalConsent> findByOrder_Id(UUID orderId);
    Optional<LegalConsent> findByContactRequest_Id(UUID contactRequestId);
}

package com.printcalculator.repository;

import com.printcalculator.entity.QrLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrLinkRepository extends JpaRepository<QrLink, UUID> {
    Optional<QrLink> findBySlug(String slug);
}

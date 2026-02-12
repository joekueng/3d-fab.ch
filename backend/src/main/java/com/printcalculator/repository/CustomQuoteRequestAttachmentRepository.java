package com.printcalculator.repository;

import com.printcalculator.entity.CustomQuoteRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomQuoteRequestAttachmentRepository extends JpaRepository<CustomQuoteRequestAttachment, UUID> {
}
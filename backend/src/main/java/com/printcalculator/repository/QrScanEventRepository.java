package com.printcalculator.repository;

import com.printcalculator.entity.QrScanEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface QrScanEventRepository extends JpaRepository<QrScanEvent, UUID> {
    List<QrScanEvent> findByQrLink_IdAndScannedAtBetweenOrderByScannedAtDesc(
            UUID qrLinkId,
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive
    );

    List<QrScanEvent> findByScannedAtBetweenOrderByScannedAtDesc(
            OffsetDateTime fromInclusive,
            OffsetDateTime toExclusive
    );
}

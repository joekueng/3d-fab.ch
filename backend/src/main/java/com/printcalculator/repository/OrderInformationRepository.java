package com.printcalculator.repository;

import com.printcalculator.entity.OrderInformation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;

public interface OrderInformationRepository extends JpaRepository<OrderInformation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from OrderInformation i where i.id = :id")
    Optional<OrderInformation> lockById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderInformation> findByOrderId(UUID orderId);
    List<OrderInformation> findByOrderIdIsNullAndExpiresAtBefore(OffsetDateTime cutoff);
    @Query("select i.orderId from OrderInformation i where i.orderId is not null and i.unreadCount > 0")
    List<UUID> orderIds();
}

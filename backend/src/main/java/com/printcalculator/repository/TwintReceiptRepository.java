package com.printcalculator.repository;

import com.printcalculator.entity.TwintReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TwintReceiptRepository extends JpaRepository<TwintReceipt, UUID> {
    boolean existsByMailboxKeyAndUidValidityAndMessageUid(String key, long validity, long uid);
    Optional<TwintReceipt> findByClaimedTransactionId(String transactionId);
    boolean existsByOrderIdAndClaimedTransactionIdIsNotNull(UUID orderId);
    boolean existsByContentHashAndOutcomeNot(String hash, String outcome);
}

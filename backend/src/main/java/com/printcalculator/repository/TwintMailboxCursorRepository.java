package com.printcalculator.repository;

import com.printcalculator.entity.TwintMailboxCursor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface TwintMailboxCursorRepository extends JpaRepository<TwintMailboxCursor, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TwintMailboxCursor c where c.id = :id")
    Optional<TwintMailboxCursor> findLockedById(String id);
}

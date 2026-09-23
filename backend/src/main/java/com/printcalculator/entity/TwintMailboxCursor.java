package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Getter
@Setter
@Table(name = "twint_mailbox_cursors")
public class TwintMailboxCursor {
    @Id
    private String id;
    private long uidValidity;
    private long lastUid;
    @Column(nullable = false)
    private OffsetDateTime initialSince;
}

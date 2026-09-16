package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

/** Private instruction drafts and immutable purchase snapshots. Files are never public media. */
@Entity
@Table(name = "order_information", indexes = @Index(name = "ix_information_expiry", columnList = "expires_at"))
@Getter @Setter
public class OrderInformation {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "order_id", unique = true) private UUID orderId;
    @Column(nullable = false) private String accessToken;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false) private List<Entry> entries = new ArrayList<>();
    @org.hibernate.annotations.ColumnDefault("0")
    @Column(nullable = false) private int unreadCount;
    public void setEntries(List<Entry> entries) {
        this.entries = entries;
        this.unreadCount = (int) entries.stream().filter(e -> e.readAt() == null).count();
    }
    public record Attachment(UUID id, String name, String mime, long size) {}
    public record Entry(UUID id, String text, String model, String modelKey, OffsetDateTime createdAt,
                        OffsetDateTime readAt, List<Attachment> attachments) {}
}

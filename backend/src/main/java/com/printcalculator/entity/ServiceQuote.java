package com.printcalculator.entity;

import com.printcalculator.dto.ServiceQuoteContent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Entity @Table(name="service_quotes")
public class ServiceQuote {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Version private long version;
    @Column(length=160) private String clientName;
    @Column(length=160) private String collaborationName;
    private UUID sourceRequestId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private ServiceQuoteContent draft;
    @Column(nullable=false) private int nextRevision = 1;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    @Column(nullable=false) private OffsetDateTime createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
}

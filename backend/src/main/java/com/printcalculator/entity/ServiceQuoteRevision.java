package com.printcalculator.entity;

import com.printcalculator.dto.ServiceQuoteContent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="service_quote_revisions", uniqueConstraints=@UniqueConstraint(name="uq_service_quote_revision",columnNames={"quote_id","revision_number"}))
public class ServiceQuoteRevision {
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="quote_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_servicequoterevision_quote"))
    private ServiceQuote quote;
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="quote_id",nullable=false,updatable=false) private UUID quoteId;
    @Column(name="revision_number",nullable=false,updatable=false) private int revisionNumber;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb") private ServiceQuoteContent content;
    @Column(nullable=false,updatable=false,precision=12,scale=2) private BigDecimal estimatedAmountChf;
    @Column(nullable=false,updatable=false,precision=12,scale=2) private BigDecimal maximumAmountChf;
    @Column(nullable=false,updatable=false,length=3000) private String acceptanceText;
    @Column(nullable=false,updatable=false,length=64) private String contentVersion;
    @Column(nullable=false,updatable=false,length=64) private String termsVersion;
    @Column(nullable=false,updatable=false,length=64) private String privacyVersion;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    @Column(nullable=false,updatable=false) private OffsetDateTime publishedAt;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    private OffsetDateTime revokedAt;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    private OffsetDateTime supersededAt;
}

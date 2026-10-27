package com.printcalculator.entity;

import com.printcalculator.dto.ServiceQuoteDtos.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Entity @Table(name="service_quote_acceptances")
public class ServiceQuoteAcceptance {
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="revision_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_servicequoteacceptance_revision"))
    private ServiceQuoteRevision revision;
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="revision_id",nullable=false,unique=true,updatable=false) private UUID revisionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,updatable=false,columnDefinition="jsonb") private Customer customer;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    @Column(nullable=false,updatable=false) private OffsetDateTime acceptedAt;
    @Column(updatable=false,length=45) private String clientIp;
    @Column(nullable=false,updatable=false) private UUID requestId;
    @Column(nullable=false,updatable=false,length=64) private String requestHash;
    @Column(nullable=false,updatable=false,length=64) private String downloadTokenHash;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    @Column(nullable=false,updatable=false) private OffsetDateTime downloadExpiresAt;
    @Column(nullable=false,updatable=false) private boolean acceptQuote;
    @Column(nullable=false,updatable=false) private boolean acceptTerms;
    @Column(nullable=false,updatable=false) private boolean acceptPrivacy;
}

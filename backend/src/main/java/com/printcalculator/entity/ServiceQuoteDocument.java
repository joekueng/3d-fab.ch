package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Entity @Table(name="service_quote_documents")
public class ServiceQuoteDocument {
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="acceptance_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_servicequotedocument_acceptance"))
    private ServiceQuoteAcceptance acceptance;
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="acceptance_id",nullable=false,unique=true,updatable=false) private UUID acceptanceId;
    @Column(nullable=false,length=20) private String status="PENDING";
    @Column(length=20) private String emailStatus="PENDING";
    @Column(length=20) private String adminEmailStatus="PENDING";
    @Column(nullable=false,length=64) private String templateVersion="service-quote-v1";
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    private OffsetDateTime generatedAt;
    @Column(length=160) private String storageKey;
    @Column(length=64) private String documentHash;
    private Long sizeBytes;
}

package com.printcalculator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Entity @Table(name="service_quote_public_links")
public class ServiceQuotePublicLink {
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="revision_id",insertable=false,updatable=false,foreignKey=@ForeignKey(name="fk_servicequotepubliclink_revision"))
    private ServiceQuoteRevision revision;
    @Id private UUID id;
    @Column(name="revision_id",nullable=false,unique=true,updatable=false) private UUID revisionId;
    @Column(nullable=false,updatable=false,length=100) private String accessToken;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    @Column(nullable=false,updatable=false) private OffsetDateTime createdAt;
    @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE)
    private OffsetDateTime revokedAt;
}

package com.printcalculator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_scan_event", indexes = {
        @Index(name = "ix_qr_scan_event_link_scanned", columnList = "qr_link_id, scanned_at"),
        @Index(name = "ix_qr_scan_event_link_bot_scanned", columnList = "qr_link_id, is_suspected_bot, scanned_at")
})
public class QrScanEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "qr_scan_event_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "qr_link_id", nullable = false)
    private QrLink qrLink;

    @ColumnDefault("now()")
    @Column(name = "scanned_at", nullable = false)
    private OffsetDateTime scannedAt;

    @Column(name = "resolved_lang", nullable = false, length = 2)
    private String resolvedLang;

    @Column(name = "final_path", nullable = false, length = Integer.MAX_VALUE)
    private String finalPath;

    @Column(name = "visitor_key_hash", nullable = false, length = 64)
    private String visitorKeyHash;

    @ColumnDefault("false")
    @Column(name = "is_suspected_bot", nullable = false)
    private Boolean isSuspectedBot;

    @Column(name = "country_code", length = 16)
    private String countryCode;

    @Column(name = "country_name", length = 128)
    private String countryName;

    @Column(name = "region_name", length = 128)
    private String regionName;

    @Column(name = "city_name", length = 128)
    private String cityName;

    @PrePersist
    private void onCreate() {
        if (scannedAt == null) {
            scannedAt = OffsetDateTime.now();
        }
        if (isSuspectedBot == null) {
            isSuspectedBot = false;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QrLink getQrLink() {
        return qrLink;
    }

    public void setQrLink(QrLink qrLink) {
        this.qrLink = qrLink;
    }

    public OffsetDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(OffsetDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }

    public String getResolvedLang() {
        return resolvedLang;
    }

    public void setResolvedLang(String resolvedLang) {
        this.resolvedLang = resolvedLang;
    }

    public String getFinalPath() {
        return finalPath;
    }

    public void setFinalPath(String finalPath) {
        this.finalPath = finalPath;
    }

    public String getVisitorKeyHash() {
        return visitorKeyHash;
    }

    public void setVisitorKeyHash(String visitorKeyHash) {
        this.visitorKeyHash = visitorKeyHash;
    }

    public Boolean getIsSuspectedBot() {
        return isSuspectedBot;
    }

    public void setIsSuspectedBot(Boolean suspectedBot) {
        isSuspectedBot = suspectedBot;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }
}

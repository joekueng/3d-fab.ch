package com.printcalculator.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "legal_consents", uniqueConstraints = {
        @UniqueConstraint(name = "uq_legal_consents_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uq_legal_consents_contact_request", columnNames = "contact_request_id")
})
public class LegalConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "legal_consent_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_request_id", updatable = false)
    private CustomQuoteRequest contactRequest;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private OffsetDateTime acceptedAt;

    @Column(name = "terms_accepted", nullable = false, updatable = false)
    private boolean termsAccepted;

    @Column(name = "privacy_accepted", nullable = false, updatable = false)
    private boolean privacyAccepted;

    @Column(name = "terms_version", nullable = false, length = 64, updatable = false)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false, length = 64, updatable = false)
    private String privacyVersion;

    @Column(name = "language", nullable = false, length = 2, updatable = false)
    private String language;

    @Column(name = "email", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String email;

    @Column(name = "client_ip", length = 45, updatable = false)
    private String clientIp;

    protected LegalConsent() {
    }

    public LegalConsent(Order order, CustomQuoteRequest contactRequest, boolean termsAccepted,
                        boolean privacyAccepted, String termsVersion, String privacyVersion, String language, String clientIp) {
        if ((order == null) == (contactRequest == null)) {
            throw new IllegalArgumentException("Legal consent must belong to exactly one order or contact request");
        }
        if (!termsAccepted || !privacyAccepted) {
            throw new IllegalArgumentException("Terms and privacy acceptance are required");
        }
        this.order = order;
        this.contactRequest = contactRequest;
        this.acceptedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.termsAccepted = termsAccepted;
        this.privacyAccepted = privacyAccepted;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.language = language;
        this.email = order != null ? order.getCustomerEmail() : contactRequest.getEmail();
        this.clientIp = clientIp;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public CustomQuoteRequest getContactRequest() { return contactRequest; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public boolean isPrivacyAccepted() { return privacyAccepted; }
    public String getTermsVersion() { return termsVersion; }
    public String getPrivacyVersion() { return privacyVersion; }
    public String getLanguage() { return language; }
    public String getEmail() { return email; }
    public String getClientIp() { return clientIp; }
}

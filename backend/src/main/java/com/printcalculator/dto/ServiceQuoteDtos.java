package com.printcalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ServiceQuoteDtos {
    private ServiceQuoteDtos() {}
    public record Draft(@NotNull @Valid ServiceQuoteContent content,
        @Size(max=160) String clientName, @Size(max=160) String collaborationName,
        UUID sourceRequestId, @NotNull @PositiveOrZero Long version) {}
    public record Version(@NotNull @PositiveOrZero Long version) {}
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email) {}
    public record Customer(
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Pattern(regexp="PRIVATE|BUSINESS") String customerType,
        @Size(max=100) String firstName, @Size(max=100) String lastName,
        @Size(max=160) String companyName, @Size(max=160) String contactPerson,
        @NotBlank @Size(max=200) String addressLine1,
        @NotBlank @Size(max=20) String zip, @NotBlank @Size(max=100) String city,
        @NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode,
        @Size(max=40) String phone
    ) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        @AssertTrue public boolean isIdentityComplete() {
            return "BUSINESS".equals(customerType) ? filled(companyName) && filled(contactPerson)
                : filled(firstName) && filled(lastName);
        }
        private static boolean filled(String s) { return s != null && !s.isBlank(); }
    }
    public record Accept(@NotNull @Valid Customer customer, @NotNull UUID requestId,
        @NotBlank @Pattern(regexp="[a-f0-9]{64}") String downloadToken,
        @NotBlank @Size(max=64) String contentVersion,
        @AssertTrue boolean acceptQuote, @AssertTrue boolean acceptTerms, @AssertTrue boolean acceptPrivacy) {}
    public record Offer(UUID quoteId, UUID revisionId, int revisionNumber, String status,
        ServiceQuoteContent content, BigDecimal estimatedAmountChf, BigDecimal maximumAmountChf,
        String acceptanceText, String contentVersion, String termsVersion, String privacyVersion) {}
    public record Resume(String status, UUID quoteId, UUID revisionId, String token) {}
    public record Confirmation(UUID acceptanceId, String documentStatus, String emailStatus,
        OffsetDateTime downloadExpiresAt) {}
    public record Acceptance(UUID id, Customer customer, OffsetDateTime acceptedAt, String clientIp,
        String documentStatus, String emailStatus, String adminEmailStatus, String documentHash) {}
    public record Revision(Offer offer, UUID publicLinkId, String publicUrl, Acceptance acceptance) {}
    public record LinkedJob(UUID sessionId, String name, UUID convertedOrderId) {}
    public record Admin(UUID id, long version, String clientName, String collaborationName,
        UUID sourceRequestId, ServiceQuoteContent draft, OffsetDateTime createdAt,
        List<Revision> revisions, List<LinkedJob> linkedJobs, List<AdminEmailLogDto> emailLogs) {}
}

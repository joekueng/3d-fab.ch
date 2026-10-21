package com.printcalculator.service;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.LegalConsent;
import com.printcalculator.entity.Order;
import com.printcalculator.repository.LegalConsentRepository;
import com.printcalculator.security.IpAddressUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class LegalConsentService {
    // Keep in sync with LEGAL.TERMS.META.VERSION and the legal update dates in every locale.
    public static final String TERMS_VERSION = "v1.1-2026-09-16";
    public static final String PRIVACY_VERSION = "2026-09-16";
    private static final Set<String> LANGUAGES = Set.of("it", "en", "de", "fr");

    private final LegalConsentRepository repository;

    public LegalConsentService(LegalConsentRepository repository) {
        this.repository = repository;
    }

    public void recordOrder(Order order, boolean acceptTerms, boolean acceptPrivacy, String language, String clientIp) {
        repository.save(new LegalConsent(order, null, acceptTerms, acceptPrivacy,
                TERMS_VERSION, PRIVACY_VERSION, normalizeLanguage(language), IpAddressUtils.normalizeIp(clientIp)));
    }

    public void recordContactRequest(CustomQuoteRequest request, boolean acceptTerms, boolean acceptPrivacy, String language, String clientIp) {
        repository.save(new LegalConsent(null, request, acceptTerms, acceptPrivacy,
                TERMS_VERSION, PRIVACY_VERSION, normalizeLanguage(language), IpAddressUtils.normalizeIp(clientIp)));
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 2) {
            normalized = normalized.substring(0, 2);
        }
        return LANGUAGES.contains(normalized) ? normalized : "it";
    }
}

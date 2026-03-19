package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.service.email.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Map;

@Service
public class CustomQuoteRequestNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(CustomQuoteRequestNotificationService.class);

    private final EmailNotificationService emailNotificationService;
    private final ContactRequestLocalizationService localizationService;

    @Value("${app.mail.contact-request.admin.enabled:true}")
    private boolean contactRequestAdminMailEnabled;

    @Value("${app.mail.contact-request.admin.address:infog@3d-fab.ch}")
    private String contactRequestAdminMailAddress;

    @Value("${app.mail.contact-request.customer.enabled:true}")
    private boolean contactRequestCustomerMailEnabled;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public CustomQuoteRequestNotificationService(EmailNotificationService emailNotificationService,
                                                 ContactRequestLocalizationService localizationService) {
        this.emailNotificationService = emailNotificationService;
        this.localizationService = localizationService;
    }

    public void sendNotifications(CustomQuoteRequest request, int attachmentsCount, String rawLanguage) {
        String language = localizationService.normalizeLanguage(rawLanguage);
        sendAdminContactRequestNotification(request, attachmentsCount);
        sendCustomerContactRequestConfirmation(request, attachmentsCount, language);
    }

    private void sendAdminContactRequestNotification(CustomQuoteRequest request, int attachmentsCount) {
        if (!contactRequestAdminMailEnabled) {
            return;
        }
        if (contactRequestAdminMailAddress == null || contactRequestAdminMailAddress.isBlank()) {
            logger.warn("Contact request admin notification enabled but no admin address configured.");
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put("createdAt", request.getCreatedAt());
        templateData.put("requestType", safeValue(request.getRequestType()));
        templateData.put("customerType", safeValue(request.getCustomerType()));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("logoUrl", buildLogoUrl());
        templateData.put("currentYear", Year.now().getValue());

        emailNotificationService.sendEmail(
                contactRequestAdminMailAddress,
                "Nuova richiesta di contatto #" + request.getId(),
                "contact-request-admin",
                templateData
        );
    }

    private void sendCustomerContactRequestConfirmation(CustomQuoteRequest request, int attachmentsCount, String language) {
        if (!contactRequestCustomerMailEnabled) {
            return;
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            logger.warn("Contact request confirmation skipped: missing customer email for request {}", request.getId());
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put(
                "createdAt",
                request.getCreatedAt().format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                .withLocale(localizationService.localeForLanguage(language))
                )
        );
        templateData.put("recipientName", localizationService.resolveRecipientName(request, language));
        templateData.put("requestType", localizationService.localizeRequestType(request.getRequestType(), language));
        templateData.put("customerType", localizationService.localizeCustomerType(request.getCustomerType(), language));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("logoUrl", buildLogoUrl());
        templateData.put("currentYear", Year.now().getValue());

        String subject = localizationService.applyCustomerContactRequestTexts(templateData, language, request.getId());

        emailNotificationService.sendEmail(
                request.getEmail(),
                subject,
                "contact-request-customer",
                templateData
        );
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private String buildLogoUrl() {
        return frontendBaseUrl.replaceAll("/+$", "") + "/assets/images/brand-logo-yellow.svg";
    }
}

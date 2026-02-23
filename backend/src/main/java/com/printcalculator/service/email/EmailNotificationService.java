package com.printcalculator.service.email;

import java.util.Map;

public interface EmailNotificationService {

    /**
     * Sends an HTML email using a Thymeleaf template.
     *
     * @param to           The recipient email address.
     * @param subject      The subject of the email.
     * @param templateName The name of the Thymeleaf template (e.g., "order-confirmation").
     * @param contextData  The data to populate the template with.
     */
    void sendEmail(String to, String subject, String templateName, Map<String, Object> contextData);

}

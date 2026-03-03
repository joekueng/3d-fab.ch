package com.printcalculator.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.core.io.ByteArrayResource;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailNotificationService implements EmailNotificationService {

    private final JavaMailSender emailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Override
    public void sendEmail(String to, String subject, String templateName, Map<String, Object> contextData) {
        sendEmailWithAttachment(to, subject, templateName, contextData, null, null);
    }

    @Override
    public void sendEmailWithAttachment(String to, String subject, String templateName, Map<String, Object> contextData, String attachmentName, byte[] attachmentData) {
        if (!mailEnabled) {
            log.info("Email sending disabled (app.mail.enabled=false). Skipping email to {}", to);
            return;
        }

        log.info("Preparing to send email to {} with template {}", to, templateName);

        try {
            Context context = new Context();
            context.setVariables(contextData);

            String process = templateEngine.process("email/" + templateName, context);
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(process, true); // true indicates HTML format

            if (attachmentName != null && attachmentData != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData));
            }

            emailSender.send(mimeMessage);
            log.info("Email successfully sent to {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
            // Non blocco l'ordine se l'email fallisce, ma loggo l'errore adeguatamente.
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}", to, e);
        }
    }
}

package com.printcalculator.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev/email")
@Profile("local")
public class DevEmailTestController {

    private final TemplateEngine templateEngine;

    public DevEmailTestController(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @GetMapping("/test-template")
    public ResponseEntity<String> testTemplate() {
        Context context = new Context();
        Map<String, Object> templateData = new HashMap<>();
        UUID orderId = UUID.randomUUID();
        templateData.put("customerName", "Mario Rossi");
        templateData.put("orderId", orderId);
        templateData.put("orderNumber", orderId.toString().split("-")[0]);
        templateData.put("orderDetailsUrl", "https://tuosito.it/it/co/" + orderId);
        templateData.put("orderDate", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        templateData.put("totalCost", "CHF 45.50");
        templateData.put("currentYear", OffsetDateTime.now().getYear());
        templateData.put("emailTitle", "Conferma ordine");
        templateData.put("headlineText", "Grazie per il tuo ordine #" + templateData.get("orderNumber"));
        templateData.put("greetingText", "Ciao Mario,");
        templateData.put("introText", "Abbiamo ricevuto il tuo ordine e iniziato l'elaborazione.");
        templateData.put("detailsTitleText", "Dettagli ordine");
        templateData.put("labelOrderNumber", "Numero ordine");
        templateData.put("labelDate", "Data");
        templateData.put("labelTotal", "Totale");
        templateData.put("orderDetailsCtaText", "Visualizza stato ordine");
        templateData.put("attachmentHintText", "In allegato trovi la conferma ordine in PDF con QR bill.");
        templateData.put("supportText", "Se hai domande, rispondi a questa email.");
        templateData.put("footerText", "Messaggio automatico di 3D-Fab.");
        
        context.setVariables(templateData);
        String html = templateEngine.process("email/order-confirmation", context);
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=utf-8")
                .body(html);
    }
}

package com.printcalculator.service.email;

import org.jsoup.Jsoup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateTest {
    @ParameterizedTest
    @ValueSource(strings = {"order-confirmation", "order-shipped", "payment-confirmed", "payment-reported", "quote-session"})
    void resolvesSharedLayoutAndPreservesContent(String template) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new Context();
        context.setVariables(Map.of(
                "logoUrl", "https://example.test/assets/images/SVG/logo-giallo-spesso.svg",
                "currentYear", 2026,
                "headlineText", "Order <confirmation>", "title", "Saved <session>",
                "footerText", "Automated <message>", "notice", "Private <link>",
                "orderDetailsUrl", "https://example.test/it/co/123",
                "resumeUrl", "https://example.test/it/calculator/basic?session=123&mode=basic"));

        String html = engine.process("email/" + template, context);
        var document = Jsoup.parse(html);
        boolean session = template.equals("quote-session");
        assertEquals(1, document.select(".container > .header").size());
        assertEquals(context.getVariable("logoUrl"), document.selectFirst(".brand-logo").attr("src"));
        assertEquals(session ? "Saved <session>" : "Order <confirmation>", document.selectFirst(".header h1").text());
        assertEquals("© 2026 3D-Fab", document.selectFirst(".footer p").text());
        assertEquals(session ? "Private <link>" : "Automated <message>", document.select(".footer p").last().text());
        assertEquals(context.getVariable(session ? "resumeUrl" : "orderDetailsUrl"), document.selectFirst(".content a").attr("href"));
        assertTrue(document.select("style").first().data().contains("max-width: 600px"));
        assertFalse(html.contains("th:replace"));
        assertTrue(document.select("confirmation, session, message, link").isEmpty());
    }
}

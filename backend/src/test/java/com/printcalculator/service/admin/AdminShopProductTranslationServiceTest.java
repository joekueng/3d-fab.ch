package com.printcalculator.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.AdminTranslateShopProductRequest;
import com.printcalculator.dto.AdminTranslateShopProductResponse;
import com.printcalculator.repository.ShopCategoryRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminShopProductTranslationServiceTest {

    @Mock
    private ShopCategoryRepository shopCategoryRepository;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void translateProduct_shouldCallOpenAiTwiceAndReturnReviewedTranslations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> capturedRequests = new CopyOnWriteArrayList<>();
        AtomicInteger requestCounter = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            capturedRequests.add(readBody(objectMapper, exchange));
            int currentRequest = requestCounter.incrementAndGet();
            String functionName = currentRequest == 1
                    ? "generate_product_translations"
                    : "review_product_translations";
            String body = functionResponse(
                    objectMapper,
                    functionName,
                    Map.of(
                            "en", localized("Desk cable clip", "Technical desk accessory", "<p>Desk cable clip for clean cable routing.</p>", "Desk cable clip | 3D fab", "Technical 3D printed desk cable clip for clean cable routing."),
                            "de", localized("Schreibtisch-Kabelhalter", "Technisches Schreibtisch-Zubehor", "<p>Kabelhalter fur einen aufgeraumten Schreibtisch.</p>", "Schreibtisch-Kabelhalter | 3D fab", "Technischer 3D-gedruckter Kabelhalter fur einen aufgeraumten Schreibtisch."),
                            "fr", localized("Support de cable de bureau", "Accessoire technique de bureau", "<p>Support de cable pour un bureau ordonne.</p>", "Support de cable de bureau | 3D fab", "Support de cable de bureau imprime en 3D pour garder un espace ordonne.")
                    )
            );
            writeJsonResponse(exchange, body);
        });
        server.start();

        when(shopCategoryRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .thenReturn(Optional.empty());

        AdminOpenAiTranslationClient translationClient = new AdminOpenAiTranslationClient(
                objectMapper,
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "gpt-5.4",
                20,
                "test-cache-key"
        );
        AdminShopProductTranslationService service = new AdminShopProductTranslationService(
                shopCategoryRepository,
                objectMapper,
                translationClient,
                "Use concise ecommerce wording."
        );

        AdminTranslateShopProductRequest payload = new AdminTranslateShopProductRequest();
        payload.setCategoryId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        payload.setSourceLanguage("it");
        payload.setOverwriteExisting(false);
        payload.setMaterialCodes(List.of("pla", "petg"));
        payload.setNames(Map.of(
                "it", "Supporto cavo scrivania",
                "en", "",
                "de", "",
                "fr", ""
        ));
        payload.setExcerpts(Map.of(
                "it", "Accessorio tecnico",
                "en", "",
                "de", "",
                "fr", ""
        ));
        payload.setDescriptions(Map.of(
                "it", "<p>Supporto per tenere i cavi ordinati sulla scrivania.</p>",
                "en", "",
                "de", "",
                "fr", ""
        ));
        payload.setSeoTitles(Map.of(
                "it", "Supporto cavo scrivania | 3D fab",
                "en", "",
                "de", "",
                "fr", ""
        ));
        payload.setSeoDescriptions(Map.of(
                "it", "Supporto tecnico stampato in 3D per tenere i cavi in ordine sulla scrivania.",
                "en", "",
                "de", "",
                "fr", ""
        ));

        AdminTranslateShopProductResponse response = service.translateProduct(payload);

        assertEquals(List.of("en", "de", "fr"), response.getTargetLanguages());
        assertEquals("Desk cable clip", response.getNames().get("en"));
        assertTrue(response.getDescriptions().get("en").contains("<p>"));
        assertEquals(2, capturedRequests.size());
        assertEquals("required", capturedRequests.get(0).path("tool_choice").asText());
        assertEquals("test-cache-key:generate", capturedRequests.get(0).path("prompt_cache_key").asText());
        assertEquals("test-cache-key:review", capturedRequests.get(1).path("prompt_cache_key").asText());
    }

    @Test
    void translateProduct_shouldSkipOpenAiWhenNoTargetLanguageNeedsUpdates() {
        ObjectMapper objectMapper = new ObjectMapper();
        AdminOpenAiTranslationClient translationClient = new AdminOpenAiTranslationClient(
                objectMapper,
                "test-key",
                "http://127.0.0.1:65535/v1",
                "gpt-5.4",
                20,
                "test-cache-key"
        );
        AdminShopProductTranslationService service = new AdminShopProductTranslationService(
                shopCategoryRepository,
                objectMapper,
                translationClient,
                ""
        );

        AdminTranslateShopProductRequest payload = new AdminTranslateShopProductRequest();
        payload.setSourceLanguage("it");
        payload.setOverwriteExisting(false);
        payload.setNames(Map.of(
                "it", "Supporto cavo scrivania",
                "en", "Desk cable clip",
                "de", "Schreibtisch-Kabelhalter",
                "fr", "Support de cable de bureau"
        ));
        payload.setExcerpts(Map.of(
                "it", "Accessorio tecnico",
                "en", "Technical desk accessory",
                "de", "Technisches Schreibtisch-Zubehor",
                "fr", "Accessoire technique de bureau"
        ));
        payload.setDescriptions(Map.of(
                "it", "<p>Descrizione</p>",
                "en", "<p>Description</p>",
                "de", "<p>Beschreibung</p>",
                "fr", "<p>Description</p>"
        ));
        payload.setSeoTitles(Map.of(
                "it", "SEO IT",
                "en", "SEO EN",
                "de", "SEO DE",
                "fr", "SEO FR"
        ));
        payload.setSeoDescriptions(Map.of(
                "it", "SEO description IT",
                "en", "SEO description EN",
                "de", "SEO description DE",
                "fr", "SEO description FR"
        ));

        AdminTranslateShopProductResponse response = service.translateProduct(payload);
        assertTrue(response.getTargetLanguages().isEmpty());
    }

    private JsonNode readBody(ObjectMapper objectMapper, HttpExchange exchange) throws IOException {
        return objectMapper.readTree(exchange.getRequestBody());
    }

    private void writeJsonResponse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String functionResponse(ObjectMapper objectMapper,
                                    String functionName,
                                    Map<String, Map<String, String>> translations) throws IOException {
        Map<String, Object> arguments = Map.of("translations", translations);
        Map<String, Object> item = Map.of(
                "type", "function_call",
                "name", functionName,
                "arguments", objectMapper.writeValueAsString(arguments)
        );
        Map<String, Object> response = Map.of(
                "id", "resp_test",
                "output", List.of(item)
        );
        return objectMapper.writeValueAsString(response);
    }

    private Map<String, String> localized(String name,
                                          String excerpt,
                                          String description,
                                          String seoTitle,
                                          String seoDescription) {
        return Map.of(
                "name", name,
                "excerpt", excerpt,
                "description", description,
                "seoTitle", seoTitle,
                "seoDescription", seoDescription
        );
    }
}

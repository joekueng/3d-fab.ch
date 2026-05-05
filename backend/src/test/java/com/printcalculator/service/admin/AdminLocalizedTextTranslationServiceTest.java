package com.printcalculator.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.AdminLocalizedTextFieldRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminLocalizedTextTranslationServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void translateLocalizedText_shouldTranslateMissingRequiredFieldsOnly() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> capturedRequests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            capturedRequests.add(readBody(objectMapper, exchange));
            writeJsonResponse(exchange, functionResponse(
                    objectMapper,
                    Map.of(
                            "en", Map.of(
                                    "title", "Shop gallery",
                                    "altText", "3D printed products in the shop gallery"
                            ),
                            "fr", Map.of(
                                    "title", "Galerie boutique",
                                    "altText", "Produits imprimes en 3D dans la galerie boutique"
                            )
                    )
            ));
        });
        server.start();

        AdminLocalizedTextTranslationService service = createService(objectMapper, server.getAddress().getPort());

        AdminTranslateLocalizedTextRequest payload = new AdminTranslateLocalizedTextRequest();
        payload.setContext("Home media HOME_SECTION / shop-gallery");
        payload.setSourceLanguage("it");
        payload.setOverwriteExisting(false);
        payload.setFields(Map.of(
                "title", field(true, Map.of(
                        "it", "Gallery shop",
                        "en", "",
                        "de", "Shop-Galerie",
                        "fr", "Galerie existante"
                )),
                "altText", field(true, Map.of(
                        "it", "Prodotti stampati in 3D nella gallery shop",
                        "en", "",
                        "de", "3D-gedruckte Produkte in der Shop-Galerie",
                        "fr", ""
                ))
        ));

        AdminTranslateLocalizedTextResponse response = service.translateLocalizedText(payload);

        assertEquals(List.of("en", "fr"), response.getTargetLanguages());
        assertEquals("Shop gallery", response.getFields().get("title").get("en"));
        assertEquals("3D printed products in the shop gallery", response.getFields().get("altText").get("en"));
        assertEquals("Produits imprimes en 3D dans la galerie boutique", response.getFields().get("altText").get("fr"));
        assertTrue(!response.getFields().get("title").containsKey("fr"));
        assertEquals(1, capturedRequests.size());
        assertEquals("required", capturedRequests.get(0).path("tool_choice").asText());
        assertEquals("test-cache-key:localized-text", capturedRequests.get(0).path("prompt_cache_key").asText());
    }

    @Test
    void translateLocalizedText_shouldOverwriteExistingTargetsWhenRequested() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> writeJsonResponse(exchange, functionResponse(
                objectMapper,
                Map.of(
                        "en", Map.of("title", "Architecture model", "description", "A custom model for design review."),
                        "de", Map.of("title", "Architekturmodell", "description", "Ein individuelles Modell fur die Designprufung."),
                        "fr", Map.of("title", "Maquette architecturale", "description", "Une maquette personnalisee pour la revue de conception.")
                )
        )));
        server.start();

        AdminLocalizedTextTranslationService service = createService(objectMapper, server.getAddress().getPort());

        AdminTranslateLocalizedTextRequest payload = new AdminTranslateLocalizedTextRequest();
        payload.setContext("Home project card");
        payload.setSourceLanguage("it");
        payload.setOverwriteExisting(true);
        payload.setFields(Map.of(
                "title", field(true, Map.of(
                        "it", "Modellino architettonico",
                        "en", "Old title",
                        "de", "Alter Titel",
                        "fr", "Ancien titre"
                )),
                "description", field(true, Map.of(
                        "it", "Modello custom per revisione di progetto.",
                        "en", "Old description",
                        "de", "Alte Beschreibung",
                        "fr", "Ancienne description"
                ))
        ));

        AdminTranslateLocalizedTextResponse response = service.translateLocalizedText(payload);

        assertEquals(List.of("en", "de", "fr"), response.getTargetLanguages());
        assertEquals("Architecture model", response.getFields().get("title").get("en"));
        assertEquals("Architekturmodell", response.getFields().get("title").get("de"));
        assertEquals("Une maquette personnalisee pour la revue de conception.", response.getFields().get("description").get("fr"));
    }

    @Test
    void translateLocalizedText_shouldSkipOpenAiWhenNoRequiredTargetIsMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        AdminOpenAiTranslationClient translationClient = new AdminOpenAiTranslationClient(
                objectMapper,
                "test-key",
                "http://127.0.0.1:65535/v1",
                "gpt-5.4",
                20,
                "test-cache-key"
        );
        AdminLocalizedTextTranslationService service = new AdminLocalizedTextTranslationService(
                objectMapper,
                translationClient
        );

        AdminTranslateLocalizedTextRequest payload = new AdminTranslateLocalizedTextRequest();
        payload.setContext("Home project card");
        payload.setSourceLanguage("it");
        payload.setOverwriteExisting(false);
        payload.setFields(Map.of(
                "eyebrow", field(false, Map.of(
                        "it", "Progetto custom",
                        "en", "",
                        "de", "",
                        "fr", ""
                )),
                "title", field(true, Map.of(
                        "it", "Modellino",
                        "en", "Model",
                        "de", "Modell",
                        "fr", "Maquette"
                )),
                "description", field(true, Map.of(
                        "it", "Descrizione",
                        "en", "Description",
                        "de", "Beschreibung",
                        "fr", "Description"
                ))
        ));

        AdminTranslateLocalizedTextResponse response = service.translateLocalizedText(payload);

        assertTrue(response.getTargetLanguages().isEmpty());
        assertTrue(response.getFields().isEmpty());
    }

    private AdminLocalizedTextTranslationService createService(ObjectMapper objectMapper, int port) {
        AdminOpenAiTranslationClient translationClient = new AdminOpenAiTranslationClient(
                objectMapper,
                "test-key",
                "http://127.0.0.1:" + port + "/v1",
                "gpt-5.4",
                20,
                "test-cache-key"
        );
        return new AdminLocalizedTextTranslationService(objectMapper, translationClient);
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
                                    Map<String, Map<String, String>> translations) throws IOException {
        Map<String, Object> arguments = Map.of("translations", translations);
        Map<String, Object> item = Map.of(
                "type", "function_call",
                "name", "translate_localized_text",
                "arguments", objectMapper.writeValueAsString(arguments)
        );
        Map<String, Object> response = Map.of(
                "id", "resp_test",
                "output", List.of(item)
        );
        return objectMapper.writeValueAsString(response);
    }

    private AdminLocalizedTextFieldRequest field(boolean required, Map<String, String> values) {
        AdminLocalizedTextFieldRequest field = new AdminLocalizedTextFieldRequest();
        field.setRequired(required);
        field.setValues(values);
        return field;
    }
}

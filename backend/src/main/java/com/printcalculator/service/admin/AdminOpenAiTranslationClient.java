package com.printcalculator.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class AdminOpenAiTranslationClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final String promptCacheKeyPrefix;

    public AdminOpenAiTranslationClient(ObjectMapper objectMapper,
                                        @Value("${openai.translation.api-key:}") String apiKey,
                                        @Value("${openai.translation.base-url:https://api.openai.com/v1}") String baseUrl,
                                        @Value("${openai.translation.model:gpt-5.4}") String model,
                                        @Value("${openai.translation.timeout-seconds:45}") long timeoutSeconds,
                                        @Value("${openai.translation.prompt-cache-key-prefix:printcalc-shop-product-translation-v1}") String promptCacheKeyPrefix) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model != null ? model.trim() : "";
        this.timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 5));
        this.promptCacheKeyPrefix = promptCacheKeyPrefix != null && !promptCacheKeyPrefix.isBlank()
                ? promptCacheKeyPrefix.trim()
                : "printcalc-shop-product-translation-v1";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    void ensureConfigured() {
        if (apiKey.isBlank() || model.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI translation is not configured on the backend"
            );
        }
    }

    JsonNode callFunction(String functionName,
                          String functionDescription,
                          String instructions,
                          String input,
                          ObjectNode parametersSchema,
                          String cacheSuffix) {
        ensureConfigured();

        ObjectNode requestPayload = objectMapper.createObjectNode();
        requestPayload.put("model", model);
        requestPayload.put("instructions", instructions);
        requestPayload.put("input", input);
        requestPayload.put("tool_choice", "required");
        requestPayload.put("temperature", 0.2);
        requestPayload.put("store", false);
        requestPayload.put("prompt_cache_key", promptCacheKeyPrefix + ":" + cacheSuffix);

        ArrayNode tools = requestPayload.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("type", "function");
        tool.put("name", functionName);
        tool.put("description", functionDescription);
        tool.put("strict", true);
        tool.set("parameters", parametersSchema);

        JsonNode responseNode = postResponsesRequest(requestPayload);
        JsonNode output = responseNode.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if ("function_call".equals(item.path("type").asText())) {
                    String arguments = item.path("arguments").asText("");
                    if (arguments.isBlank()) {
                        break;
                    }
                    try {
                        return objectMapper.readTree(arguments);
                    } catch (JsonProcessingException exception) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "OpenAI returned invalid JSON arguments",
                                exception
                        );
                    }
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "OpenAI did not return the expected function call"
        );
    }

    private JsonNode postResponsesRequest(ObjectNode requestPayload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/responses"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(requestPayload)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = readJson(response.body());
            if (response.statusCode() >= 400) {
                String message = body.path("error").path("message").asText("").trim();
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        message.isBlank() ? "OpenAI translation request failed" : message
                );
            }
            return body;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to read the OpenAI translation response",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The OpenAI translation request was interrupted",
                    exception
            );
        }
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        String normalized = rawBaseUrl != null && !rawBaseUrl.isBlank()
                ? rawBaseUrl.trim()
                : "https://api.openai.com/v1";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize translation payload",
                    exception
            );
        }
    }

    private JsonNode readJson(String rawJson) throws IOException {
        return objectMapper.readTree(rawJson);
    }
}

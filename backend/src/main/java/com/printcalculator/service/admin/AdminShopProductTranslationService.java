package com.printcalculator.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.dto.AdminTranslateShopProductRequest;
import com.printcalculator.dto.AdminTranslateShopProductResponse;
import com.printcalculator.entity.ShopCategory;
import com.printcalculator.repository.ShopCategoryRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminShopProductTranslationService {
    private static final List<String> SUPPORTED_LANGUAGES = List.of("it", "en", "de", "fr");
    private static final Safelist PRODUCT_DESCRIPTION_SAFELIST = Safelist.none()
            .addTags("p", "div", "br", "strong", "b", "em", "i", "u", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto", "tel");
    private static final String DEFAULT_SHOP_CONTEXT = """
            3D fab is a Swiss-based 3D printing shop and technical service.
            The tone must be practical, clear, technical, and trustworthy.
            Avoid hype, avoid invented claims, and avoid vague marketing filler.
            Preserve all brand names, measurements, materials, SKUs, codes, and technical terminology exactly when they should not be translated.
            When the source field is empty, return an empty string rather than inventing content.
            For descriptions, preserve safe HTML structure when present and keep output ready for an ecommerce/admin form.
            For SEO, prefer concise, natural phrases suitable for ecommerce and search snippets.
            """;

    private final ShopCategoryRepository shopCategoryRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final String promptCacheKeyPrefix;
    private final String additionalBusinessContext;

    public AdminShopProductTranslationService(ShopCategoryRepository shopCategoryRepository,
                                              ObjectMapper objectMapper,
                                              @Value("${openai.translation.api-key:}") String apiKey,
                                              @Value("${openai.translation.base-url:https://api.openai.com/v1}") String baseUrl,
                                              @Value("${openai.translation.model:gpt-5.4}") String model,
                                              @Value("${openai.translation.timeout-seconds:45}") long timeoutSeconds,
                                              @Value("${openai.translation.prompt-cache-key-prefix:printcalc-shop-product-translation-v1}") String promptCacheKeyPrefix,
                                              @Value("${openai.translation.business-context:}") String additionalBusinessContext) {
        this.shopCategoryRepository = shopCategoryRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model != null ? model.trim() : "";
        this.timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 5));
        this.promptCacheKeyPrefix = promptCacheKeyPrefix != null && !promptCacheKeyPrefix.isBlank()
                ? promptCacheKeyPrefix.trim()
                : "printcalc-shop-product-translation-v1";
        this.additionalBusinessContext = additionalBusinessContext != null ? additionalBusinessContext.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    public AdminTranslateShopProductResponse translateProduct(AdminTranslateShopProductRequest payload) {
        ensureConfigured();
        NormalizedTranslationRequest normalizedRequest = normalizeRequest(payload);
        List<String> targetLanguages = resolveTargetLanguages(normalizedRequest);
        if (targetLanguages.isEmpty()) {
            return emptyResponse(normalizedRequest.sourceLanguage());
        }

        CategoryContext categoryContext = loadCategoryContext(normalizedRequest.categoryId());
        String businessContext = buildBusinessContext(categoryContext, normalizedRequest.materialCodes());

        TranslationBundle generated = callOpenAiFunction(
                "generate_product_translations",
                "Generate translated product copy for the requested target languages.",
                buildInstructions("Generate the first-pass translations.", businessContext),
                buildGenerationInput(normalizedRequest, targetLanguages, categoryContext),
                buildTranslationToolSchema(targetLanguages),
                "generate"
        );

        TranslationBundle normalizedGenerated = sanitizeBundle(generated, targetLanguages);
        List<String> validationNotes = buildValidationNotes(normalizedGenerated, targetLanguages);

        TranslationBundle reviewed = callOpenAiFunction(
                "review_product_translations",
                "Review and correct translated product copy while preserving meaning, SEO limits, and technical terminology.",
                buildInstructions("Review and correct the generated translations.", businessContext),
                buildReviewInput(normalizedRequest, normalizedGenerated, targetLanguages, categoryContext, validationNotes),
                buildTranslationToolSchema(targetLanguages),
                "review"
        );

        TranslationBundle finalBundle = sanitizeBundle(reviewed, targetLanguages);
        ensureRequiredTranslations(finalBundle, targetLanguages);
        return toResponse(normalizedRequest.sourceLanguage(), targetLanguages, finalBundle);
    }

    private void ensureConfigured() {
        if (apiKey.isBlank() || model.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI translation is not configured on the backend"
            );
        }
    }

    private NormalizedTranslationRequest normalizeRequest(AdminTranslateShopProductRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Translation payload is required");
        }

        String sourceLanguage = normalizeLanguage(payload.getSourceLanguage());
        if (!SUPPORTED_LANGUAGES.contains(sourceLanguage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source language");
        }

        Map<String, String> names = normalizeLocalizedMap(payload.getNames(), false);
        Map<String, String> excerpts = normalizeLocalizedMap(payload.getExcerpts(), false);
        Map<String, String> descriptions = normalizeLocalizedMap(payload.getDescriptions(), true);
        Map<String, String> seoTitles = normalizeLocalizedMap(payload.getSeoTitles(), false);
        Map<String, String> seoDescriptions = normalizeLocalizedMap(payload.getSeoDescriptions(), false);

        if (names.get(sourceLanguage).isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The active source language must have a product name before translation"
            );
        }

        Set<String> materialCodes = new LinkedHashSet<>();
        if (payload.getMaterialCodes() != null) {
            for (String materialCode : payload.getMaterialCodes()) {
                String normalizedCode = normalizeOptional(materialCode);
                if (normalizedCode != null) {
                    materialCodes.add(normalizedCode.toUpperCase(Locale.ROOT));
                }
            }
        }

        return new NormalizedTranslationRequest(
                payload.getCategoryId(),
                sourceLanguage,
                Boolean.TRUE.equals(payload.getOverwriteExisting()),
                List.copyOf(materialCodes),
                names,
                excerpts,
                descriptions,
                seoTitles,
                seoDescriptions
        );
    }

    private List<String> resolveTargetLanguages(NormalizedTranslationRequest request) {
        List<String> targetLanguages = new ArrayList<>();
        for (String language : SUPPORTED_LANGUAGES) {
            if (language.equals(request.sourceLanguage())) {
                continue;
            }
            if (request.overwriteExisting() || needsTranslation(request, language)) {
                targetLanguages.add(language);
            }
        }
        return targetLanguages;
    }

    private boolean needsTranslation(NormalizedTranslationRequest request, String language) {
        return request.names().get(language).isBlank()
                || request.excerpts().get(language).isBlank()
                || normalizeRichTextOptional(request.descriptions().get(language)) == null
                || request.seoTitles().get(language).isBlank()
                || request.seoDescriptions().get(language).isBlank();
    }

    private CategoryContext loadCategoryContext(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        ShopCategory category = shopCategoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            return null;
        }
        return new CategoryContext(
                category.getSlug(),
                Map.of(
                        "it", safeValue(category.getNameIt()),
                        "en", safeValue(category.getNameEn()),
                        "de", safeValue(category.getNameDe()),
                        "fr", safeValue(category.getNameFr())
                ),
                Map.of(
                        "it", safeValue(category.getDescriptionIt()),
                        "en", safeValue(category.getDescriptionEn()),
                        "de", safeValue(category.getDescriptionDe()),
                        "fr", safeValue(category.getDescriptionFr())
                )
        );
    }

    private String buildBusinessContext(CategoryContext categoryContext, List<String> materialCodes) {
        StringBuilder context = new StringBuilder(DEFAULT_SHOP_CONTEXT);
        if (!additionalBusinessContext.isBlank()) {
            context.append('\n').append(additionalBusinessContext.trim());
        }
        if (categoryContext != null) {
            context.append("\nCategory slug: ").append(categoryContext.slug());
            context.append("\nCategory names: ").append(writeJson(categoryContext.names()));
            if (categoryContext.descriptions().values().stream().anyMatch(value -> !value.isBlank())) {
                context.append("\nCategory descriptions: ").append(writeJson(categoryContext.descriptions()));
            }
        }
        if (materialCodes != null && !materialCodes.isEmpty()) {
            context.append("\nMaterial codes present in the product: ").append(String.join(", ", materialCodes));
        }
        return context.toString();
    }

    private String buildInstructions(String task, String businessContext) {
        return """
                You are a senior ecommerce localization editor.
                Task: %s
                Return only the function call arguments that match the provided schema.
                Always preserve meaning, HTML safety, and technical precision.
                Never invent specifications or marketing claims not present in the source.
                If a source field is empty, return an empty string for that field.
                General context:
                %s
                """.formatted(task, businessContext);
    }

    private String buildGenerationInput(NormalizedTranslationRequest request,
                                        List<String> targetLanguages,
                                        CategoryContext categoryContext) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("sourceLanguage", request.sourceLanguage());
        input.set("targetLanguages", objectMapper.valueToTree(targetLanguages));
        input.put("overwriteExisting", request.overwriteExisting());
        input.set("source", localizedFieldNode(request, request.sourceLanguage()));
        input.set("existingTranslations", existingTranslationsNode(request, targetLanguages));
        input.set("materialCodes", objectMapper.valueToTree(request.materialCodes()));
        if (categoryContext != null) {
            input.put("categorySlug", categoryContext.slug());
            input.set("categoryNames", objectMapper.valueToTree(categoryContext.names()));
        }
        return writeJson(input);
    }

    private String buildReviewInput(NormalizedTranslationRequest request,
                                    TranslationBundle generated,
                                    List<String> targetLanguages,
                                    CategoryContext categoryContext,
                                    List<String> validationNotes) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("sourceLanguage", request.sourceLanguage());
        input.set("targetLanguages", objectMapper.valueToTree(targetLanguages));
        input.set("source", localizedFieldNode(request, request.sourceLanguage()));
        input.set("generatedTranslations", generated.toJsonNode(objectMapper));
        input.set("validationNotes", objectMapper.valueToTree(validationNotes));
        input.set("materialCodes", objectMapper.valueToTree(request.materialCodes()));
        if (categoryContext != null) {
            input.put("categorySlug", categoryContext.slug());
            input.set("categoryNames", objectMapper.valueToTree(categoryContext.names()));
        }
        return writeJson(input);
    }

    private ObjectNode localizedFieldNode(NormalizedTranslationRequest request, String language) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", request.names().get(language));
        node.put("excerpt", request.excerpts().get(language));
        node.put("description", request.descriptions().get(language));
        node.put("seoTitle", request.seoTitles().get(language));
        node.put("seoDescription", request.seoDescriptions().get(language));
        return node;
    }

    private ObjectNode existingTranslationsNode(NormalizedTranslationRequest request, List<String> targetLanguages) {
        ObjectNode node = objectMapper.createObjectNode();
        for (String language : targetLanguages) {
            node.set(language, localizedFieldNode(request, language));
        }
        return node;
    }

    private ObjectNode buildTranslationToolSchema(List<String> targetLanguages) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);

        ObjectNode properties = root.putObject("properties");
        ObjectNode translations = properties.putObject("translations");
        translations.put("type", "object");
        translations.put("additionalProperties", false);

        ObjectNode translationProperties = translations.putObject("properties");
        ArrayNode requiredTranslations = translations.putArray("required");
        for (String language : targetLanguages) {
            translationProperties.set(language, buildTranslationSchemaForLanguage(language));
            requiredTranslations.add(language);
        }

        ArrayNode required = root.putArray("required");
        required.add("translations");
        return root;
    }

    private ObjectNode buildTranslationSchemaForLanguage(String language) {
        ObjectNode languageSchema = objectMapper.createObjectNode();
        languageSchema.put("type", "object");
        languageSchema.put("additionalProperties", false);
        languageSchema.put("description", "Localized product copy for language " + language);

        ObjectNode properties = languageSchema.putObject("properties");
        addSchemaString(properties, "name", "Translated product name. Never empty.");
        addSchemaString(properties, "excerpt", "Short excerpt. Empty string if source excerpt is empty.");
        addSchemaString(properties, "description", "Product description as safe HTML or empty string if source description is empty.");
        addSchemaString(properties, "seoTitle", "SEO title. Empty string if source SEO title is empty.");
        addSchemaString(properties, "seoDescription", "SEO description, ideally under 160 characters. Empty string if source SEO description is empty.");

        ArrayNode required = languageSchema.putArray("required");
        required.add("name");
        required.add("excerpt");
        required.add("description");
        required.add("seoTitle");
        required.add("seoDescription");
        return languageSchema;
    }

    private void addSchemaString(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    private TranslationBundle callOpenAiFunction(String functionName,
                                                 String functionDescription,
                                                 String instructions,
                                                 String input,
                                                 ObjectNode parametersSchema,
                                                 String cacheSuffix) {
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
                        JsonNode argumentsNode = objectMapper.readTree(arguments);
                        JsonNode translationsNode = argumentsNode.path("translations");
                        if (!translationsNode.isObject()) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY,
                                    "OpenAI returned a function call without translations"
                            );
                        }
                        return TranslationBundle.fromJson(translationsNode);
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

    private List<String> buildValidationNotes(TranslationBundle bundle, List<String> targetLanguages) {
        List<String> notes = new ArrayList<>();
        for (String language : targetLanguages) {
            if (bundle.names().getOrDefault(language, "").isBlank()) {
                notes.add(language + ": translated name is empty and must be fixed");
            }
            String seoDescription = bundle.seoDescriptions().getOrDefault(language, "");
            if (seoDescription.length() > 160) {
                notes.add(language + ": seoDescription exceeds 160 characters and must be shortened");
            }
            String description = bundle.descriptions().getOrDefault(language, "");
            if (!description.isBlank() && normalizeRichTextOptional(description) == null) {
                notes.add(language + ": description lost meaningful text during sanitization");
            }
        }
        if (notes.isEmpty()) {
            notes.add("No structural validation issues were found. Review naturalness, terminology, SEO clarity, and consistency.");
        }
        return notes;
    }

    private TranslationBundle sanitizeBundle(TranslationBundle bundle, List<String> targetLanguages) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> excerpts = new LinkedHashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        Map<String, String> seoTitles = new LinkedHashMap<>();
        Map<String, String> seoDescriptions = new LinkedHashMap<>();

        for (String language : targetLanguages) {
            names.put(language, safeValue(bundle.names().get(language)));
            excerpts.put(language, safeValue(bundle.excerpts().get(language)));
            descriptions.put(language, safeDescription(bundle.descriptions().get(language)));
            seoTitles.put(language, safeValue(bundle.seoTitles().get(language)));
            seoDescriptions.put(language, limitSeoDescription(safeValue(bundle.seoDescriptions().get(language))));
        }

        return new TranslationBundle(names, excerpts, descriptions, seoTitles, seoDescriptions);
    }

    private void ensureRequiredTranslations(TranslationBundle bundle, List<String> targetLanguages) {
        for (String language : targetLanguages) {
            if (bundle.names().getOrDefault(language, "").isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OpenAI did not return a valid translated name for " + language.toUpperCase(Locale.ROOT)
                );
            }
        }
    }

    private AdminTranslateShopProductResponse toResponse(String sourceLanguage,
                                                         List<String> targetLanguages,
                                                         TranslationBundle bundle) {
        AdminTranslateShopProductResponse response = new AdminTranslateShopProductResponse();
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguages(targetLanguages);
        response.setNames(bundle.names());
        response.setExcerpts(bundle.excerpts());
        response.setDescriptions(bundle.descriptions());
        response.setSeoTitles(bundle.seoTitles());
        response.setSeoDescriptions(bundle.seoDescriptions());
        return response;
    }

    private AdminTranslateShopProductResponse emptyResponse(String sourceLanguage) {
        AdminTranslateShopProductResponse response = new AdminTranslateShopProductResponse();
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguages(List.of());
        response.setNames(Map.of());
        response.setExcerpts(Map.of());
        response.setDescriptions(Map.of());
        response.setSeoTitles(Map.of());
        response.setSeoDescriptions(Map.of());
        return response;
    }

    private Map<String, String> normalizeLocalizedMap(Map<String, String> rawValues, boolean richText) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String language : SUPPORTED_LANGUAGES) {
            String value = rawValues != null ? rawValues.get(language) : null;
            if (richText) {
                normalized.put(language, normalizeRichTextOptional(value) != null ? normalizeRichTextOptional(value) : "");
            } else {
                normalized.put(language, safeValue(value));
            }
        }
        return normalized;
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeDescription(String value) {
        String normalized = normalizeRichTextOptional(value);
        return normalized != null ? normalized : "";
    }

    private String limitSeoDescription(String value) {
        String normalized = safeValue(value);
        if (normalized.length() <= 160) {
            return normalized;
        }
        int lastSpace = normalized.lastIndexOf(' ', 157);
        if (lastSpace >= 120) {
            return normalized.substring(0, lastSpace).trim();
        }
        return normalized.substring(0, 160).trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex > 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        return normalized;
    }

    private String normalizeRichTextOptional(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = Jsoup.clean(
                normalized,
                "",
                PRODUCT_DESCRIPTION_SAFELIST,
                new Document.OutputSettings().prettyPrint(false)
        ).trim();
        if (sanitized.isBlank()) {
            return null;
        }

        String plainText = Jsoup.parse(sanitized).text();
        return plainText != null && !plainText.trim().isEmpty() ? sanitized : null;
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

    private record NormalizedTranslationRequest(UUID categoryId,
                                                String sourceLanguage,
                                                boolean overwriteExisting,
                                                List<String> materialCodes,
                                                Map<String, String> names,
                                                Map<String, String> excerpts,
                                                Map<String, String> descriptions,
                                                Map<String, String> seoTitles,
                                                Map<String, String> seoDescriptions) {
    }

    private record CategoryContext(String slug,
                                   Map<String, String> names,
                                   Map<String, String> descriptions) {
    }

    private record TranslationBundle(Map<String, String> names,
                                     Map<String, String> excerpts,
                                     Map<String, String> descriptions,
                                     Map<String, String> seoTitles,
                                     Map<String, String> seoDescriptions) {
        static TranslationBundle fromJson(JsonNode translationsNode) {
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, String> excerpts = new LinkedHashMap<>();
            Map<String, String> descriptions = new LinkedHashMap<>();
            Map<String, String> seoTitles = new LinkedHashMap<>();
            Map<String, String> seoDescriptions = new LinkedHashMap<>();

            translationsNode.fieldNames().forEachRemaining(language -> {
                JsonNode localizedNode = translationsNode.path(language);
                names.put(language, localizedNode.path("name").asText(""));
                excerpts.put(language, localizedNode.path("excerpt").asText(""));
                descriptions.put(language, localizedNode.path("description").asText(""));
                seoTitles.put(language, localizedNode.path("seoTitle").asText(""));
                seoDescriptions.put(language, localizedNode.path("seoDescription").asText(""));
            });

            return new TranslationBundle(names, excerpts, descriptions, seoTitles, seoDescriptions);
        }

        ObjectNode toJsonNode(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode translations = root.putObject("translations");
            for (String language : names.keySet()) {
                ObjectNode languageNode = translations.putObject(language);
                languageNode.put("name", names.getOrDefault(language, ""));
                languageNode.put("excerpt", excerpts.getOrDefault(language, ""));
                languageNode.put("description", descriptions.getOrDefault(language, ""));
                languageNode.put("seoTitle", seoTitles.getOrDefault(language, ""));
                languageNode.put("seoDescription", seoDescriptions.getOrDefault(language, ""));
            }
            return root;
        }
    }
}

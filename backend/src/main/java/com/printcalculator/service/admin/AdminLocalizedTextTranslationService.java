package com.printcalculator.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.printcalculator.dto.AdminLocalizedTextFieldRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextRequest;
import com.printcalculator.dto.AdminTranslateLocalizedTextResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AdminLocalizedTextTranslationService {

    private static final List<String> SUPPORTED_LANGUAGES = List.of("it", "en", "de", "fr");
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final String DEFAULT_CONTEXT = """
            3D fab is a Swiss-based 3D printing shop and technical service.
            The tone must be practical, clear, technical, and trustworthy.
            Avoid hype, invented claims, and vague marketing filler.
            Preserve brand names, measurements, materials, SKUs, codes, and technical terminology exactly when appropriate.
            """;

    private final ObjectMapper objectMapper;
    private final AdminOpenAiTranslationClient translationClient;

    public AdminLocalizedTextTranslationService(ObjectMapper objectMapper,
                                                AdminOpenAiTranslationClient translationClient) {
        this.objectMapper = objectMapper;
        this.translationClient = translationClient;
    }

    public AdminTranslateLocalizedTextResponse translateLocalizedText(AdminTranslateLocalizedTextRequest payload) {
        translationClient.ensureConfigured();
        NormalizedLocalizedTextRequest request = normalizeRequest(payload);
        List<String> targetLanguages = resolveTargetLanguages(request);
        if (targetLanguages.isEmpty()) {
            return emptyResponse(request.sourceLanguage());
        }

        JsonNode argumentsNode = translationClient.callFunction(
                "translate_localized_text",
                "Translate localized admin text fields for the requested target languages.",
                buildInstructions(request.context()),
                buildTranslationInput(request, targetLanguages),
                buildTranslationToolSchema(request.fields(), targetLanguages),
                "localized-text"
        );

        JsonNode translationsNode = argumentsNode.path("translations");
        if (!translationsNode.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI returned a function call without translations"
            );
        }

        Map<String, Map<String, String>> translatedFields =
                sanitizeTranslations(translationsNode, request, targetLanguages);
        ensureRequiredTranslations(translatedFields, request, targetLanguages);
        return toResponse(request.sourceLanguage(), targetLanguages, translatedFields);
    }

    private NormalizedLocalizedTextRequest normalizeRequest(AdminTranslateLocalizedTextRequest payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Translation payload is required");
        }

        String sourceLanguage = normalizeLanguage(payload.getSourceLanguage());
        if (!SUPPORTED_LANGUAGES.contains(sourceLanguage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source language");
        }

        Map<String, NormalizedField> fields = new LinkedHashMap<>();
        if (payload.getFields() == null || payload.getFields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one localized field is required");
        }

        for (Map.Entry<String, AdminLocalizedTextFieldRequest> entry : payload.getFields().entrySet()) {
            String fieldName = normalizeFieldName(entry.getKey());
            AdminLocalizedTextFieldRequest fieldRequest = entry.getValue();
            if (fieldRequest == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Localized field payload is required");
            }

            Map<String, String> values = normalizeLocalizedMap(fieldRequest.getValues());
            boolean required = !Boolean.FALSE.equals(fieldRequest.getRequired());
            fields.put(fieldName, new NormalizedField(fieldName, required, values));
        }

        for (NormalizedField field : fields.values()) {
            if (field.required() && field.values().get(sourceLanguage).isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The active source language must have all required fields before translation"
                );
            }
        }

        String context = normalizeOptional(payload.getContext());
        return new NormalizedLocalizedTextRequest(
                context != null ? context : "Admin localized text",
                sourceLanguage,
                Boolean.TRUE.equals(payload.getOverwriteExisting()),
                fields
        );
    }

    private List<String> resolveTargetLanguages(NormalizedLocalizedTextRequest request) {
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

    private boolean needsTranslation(NormalizedLocalizedTextRequest request, String language) {
        for (NormalizedField field : request.fields().values()) {
            if (field.required()
                    && !field.values().get(request.sourceLanguage()).isBlank()
                    && field.values().get(language).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String buildInstructions(String context) {
        return """
                You are a senior localization editor for admin-managed website text.
                Translate only the provided fields into the requested target languages.
                Return only the function call arguments that match the provided schema.
                Never invent details, claims, locations, specifications, or products not present in the source.
                Preserve concise wording suitable for image metadata, home page project cards, and operational admin forms.
                If a source field is empty, return an empty string for that field.
                General context:
                %s
                Specific context:
                %s
                """.formatted(DEFAULT_CONTEXT, context);
    }

    private String buildTranslationInput(NormalizedLocalizedTextRequest request, List<String> targetLanguages) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("sourceLanguage", request.sourceLanguage());
        input.set("targetLanguages", objectMapper.valueToTree(targetLanguages));
        input.put("overwriteExisting", request.overwriteExisting());
        input.put("context", request.context());
        input.set("source", fieldValuesForLanguage(request, request.sourceLanguage()));
        input.set("existingTranslations", existingTranslationsNode(request, targetLanguages));
        input.set("requiredFields", objectMapper.valueToTree(requiredFieldNames(request)));
        return writeJson(input);
    }

    private ObjectNode fieldValuesForLanguage(NormalizedLocalizedTextRequest request, String language) {
        ObjectNode node = objectMapper.createObjectNode();
        for (NormalizedField field : request.fields().values()) {
            node.put(field.name(), field.values().get(language));
        }
        return node;
    }

    private ObjectNode existingTranslationsNode(NormalizedLocalizedTextRequest request, List<String> targetLanguages) {
        ObjectNode node = objectMapper.createObjectNode();
        for (String language : targetLanguages) {
            node.set(language, fieldValuesForLanguage(request, language));
        }
        return node;
    }

    private List<String> requiredFieldNames(NormalizedLocalizedTextRequest request) {
        return request.fields().values().stream()
                .filter(NormalizedField::required)
                .map(NormalizedField::name)
                .toList();
    }

    private ObjectNode buildTranslationToolSchema(Map<String, NormalizedField> fields, List<String> targetLanguages) {
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
            translationProperties.set(language, buildTranslationSchemaForLanguage(fields, language));
            requiredTranslations.add(language);
        }

        ArrayNode required = root.putArray("required");
        required.add("translations");
        return root;
    }

    private ObjectNode buildTranslationSchemaForLanguage(Map<String, NormalizedField> fields, String language) {
        ObjectNode languageSchema = objectMapper.createObjectNode();
        languageSchema.put("type", "object");
        languageSchema.put("additionalProperties", false);
        languageSchema.put("description", "Localized field values for language " + language);

        ObjectNode properties = languageSchema.putObject("properties");
        ArrayNode required = languageSchema.putArray("required");
        for (NormalizedField field : fields.values()) {
            ObjectNode property = properties.putObject(field.name());
            property.put("type", "string");
            property.put("description", field.required()
                    ? "Translated required field. Return an empty string only when the source is empty."
                    : "Translated optional field. Return an empty string when the source is empty.");
            required.add(field.name());
        }
        return languageSchema;
    }

    private Map<String, Map<String, String>> sanitizeTranslations(JsonNode translationsNode,
                                                                  NormalizedLocalizedTextRequest request,
                                                                  List<String> targetLanguages) {
        Map<String, Map<String, String>> translatedFields = new LinkedHashMap<>();
        for (NormalizedField field : request.fields().values()) {
            Map<String, String> valuesByLanguage = new LinkedHashMap<>();
            for (String language : targetLanguages) {
                String currentValue = field.values().get(language);
                if (!request.overwriteExisting() && !currentValue.isBlank()) {
                    continue;
                }
                String translatedValue = safeValue(
                        translationsNode.path(language).path(field.name()).asText("")
                );
                if (!translatedValue.isBlank()) {
                    valuesByLanguage.put(language, translatedValue);
                }
            }
            if (!valuesByLanguage.isEmpty()) {
                translatedFields.put(field.name(), valuesByLanguage);
            }
        }
        return translatedFields;
    }

    private void ensureRequiredTranslations(Map<String, Map<String, String>> translatedFields,
                                            NormalizedLocalizedTextRequest request,
                                            List<String> targetLanguages) {
        for (String language : targetLanguages) {
            for (NormalizedField field : request.fields().values()) {
                if (!field.required()) {
                    continue;
                }
                if (!request.overwriteExisting() && !field.values().get(language).isBlank()) {
                    continue;
                }
                if (field.values().get(request.sourceLanguage()).isBlank()) {
                    continue;
                }
                if (translatedFields.getOrDefault(field.name(), Map.of()).getOrDefault(language, "").isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "OpenAI did not return a valid translated " + field.name() + " for "
                                    + language.toUpperCase(Locale.ROOT)
                    );
                }
            }
        }
    }

    private AdminTranslateLocalizedTextResponse toResponse(String sourceLanguage,
                                                           List<String> targetLanguages,
                                                           Map<String, Map<String, String>> fields) {
        AdminTranslateLocalizedTextResponse response = new AdminTranslateLocalizedTextResponse();
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguages(targetLanguages);
        response.setFields(fields);
        return response;
    }

    private AdminTranslateLocalizedTextResponse emptyResponse(String sourceLanguage) {
        AdminTranslateLocalizedTextResponse response = new AdminTranslateLocalizedTextResponse();
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguages(List.of());
        response.setFields(Map.of());
        return response;
    }

    private Map<String, String> normalizeLocalizedMap(Map<String, String> rawValues) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String language : SUPPORTED_LANGUAGES) {
            String value = rawValues != null ? rawValues.get(language) : null;
            normalized.put(language, safeValue(value));
        }
        return normalized;
    }

    private String normalizeFieldName(String fieldName) {
        String normalized = normalizeOptional(fieldName);
        if (normalized == null || !FIELD_NAME_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid localized field name");
        }
        return normalized;
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

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
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

    private record NormalizedLocalizedTextRequest(String context,
                                                  String sourceLanguage,
                                                  boolean overwriteExisting,
                                                  Map<String, NormalizedField> fields) {
    }

    private record NormalizedField(String name,
                                   boolean required,
                                   Map<String, String> values) {
    }
}

package com.yourcompany.testing.infrastructure.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.application.port.DomPrivacySanitizer;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DomSanitizer implements DomPrivacySanitizer {
    private static final Set<String> PERMANENTLY_BLOCKED_ATTRIBUTES = Set.of("value", "src", "srcset");

    private final ObjectMapper mapper;
    private final Set<String> allowedAttributes;
    private final Set<String> excludedAttributes;
    private final Set<String> excludedElementHints;
    private final Set<String> redactedTerms;
    private final java.util.List<Pattern> redactedPatterns;

    public DomSanitizer(ApplicationConfiguration.DomPrivacyConfig configuration) {
        this.mapper = new ObjectMapper();
        this.allowedAttributes = lowerCaseSet(configuration.allowedAttributes);
        this.excludedAttributes = lowerCaseSet(configuration.excludedAttributes);
        this.excludedElementHints = lowerCaseSet(configuration.excludedElementHints);
        this.redactedTerms = lowerCaseSet(configuration.redactedTerms);
        this.redactedPatterns = configuration.redactedPatterns == null ? java.util.List.of() : configuration.redactedPatterns.stream().filter(value -> value != null && !value.isBlank()).map(Pattern::compile).toList();
    }

    public String sanitize(String rawDomSnapshot) {
        if (StringUtils.isBlank(rawDomSnapshot)) return "[]";

        try {
            JsonNode root = mapper.readTree(rawDomSnapshot);

            if (!root.isArray()) throw new IllegalArgumentException("DOM snapshot must be a JSON array");

            ArrayNode sanitizedElements = mapper.createArrayNode();

            for (JsonNode elementNode : root) {
                if (!elementNode.isObject() || isExcludedElement(elementNode)) continue;

                ObjectNode sanitizedElement = mapper.createObjectNode();

                copySafeText(elementNode, sanitizedElement, "tag");
                copySafeText(elementNode, sanitizedElement, "label");
                copySafeText(elementNode, sanitizedElement, "text");

                ObjectNode sanitizedAttributes = sanitizedElement.putObject("attributes");

                JsonNode attributes = elementNode.path("attributes");

                if (!attributes.isObject())
                    continue;

                attributes.fields().forEachRemaining(entry -> {
                    String attributeName = entry.getKey().toLowerCase(Locale.ROOT);

                    if (!isAllowedAttribute(attributeName)) return;

                    String attributeValue = redact(entry.getValue().asText(""));

                    if (StringUtils.isNotBlank(attributeValue)) sanitizedAttributes.put(attributeName, attributeValue);
                });

                sanitizedElements.add(sanitizedElement);
            }

            return mapper.writeValueAsString(sanitizedElements);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sanitize DOM snapshot before AI use", exception);
        }
    }

    private boolean isAllowedAttribute(String attributeName) {
        return !PERMANENTLY_BLOCKED_ATTRIBUTES.contains(attributeName) &&
                !excludedAttributes.contains(attributeName) &&
                (allowedAttributes.isEmpty() || allowedAttributes.contains(attributeName));
    }

    private boolean isExcludedElement(JsonNode elementNode) {
        StringBuilder searchable = new StringBuilder();

        searchable.append(elementNode.path("tag").asText(""))
                .append(' ')
                .append(elementNode.path("label").asText(""))
                .append(' ')
                .append(elementNode.path("text").asText(""));

        JsonNode attributes = elementNode.path("attributes");

        if (attributes.isObject())
            attributes.fields().forEachRemaining(entry -> searchable
                    .append(' ')
                    .append(entry.getKey())
                    .append(' ')
                    .append(entry.getValue().asText("")));

        String normalized = searchable.toString().toLowerCase(Locale.ROOT);

        return excludedElementHints.stream().anyMatch(hint -> StringUtils.isNotBlank(hint) && normalized.contains(hint));
    }

    private void copySafeText(JsonNode source, ObjectNode target, String fieldName) {
        String sanitizedValue = redact(source.path(fieldName).asText(""));

        if (StringUtils.isNotBlank(sanitizedValue)) target.put(fieldName, sanitizedValue);
    }

    private String redact(String value) {
        String redacted = value == null ? "" : value;

        for (String term : redactedTerms)
            if (!StringUtils.isBlank(term)) redacted = redacted.replaceAll("(?i)" + Pattern.quote(term), "[REDACTED]");

        for (Pattern pattern : redactedPatterns)
            redacted = pattern.matcher(redacted).replaceAll("[REDACTED]");

        return redacted;
    }

    private static Set<String> lowerCaseSet(java.util.List<String> values) {
        Set<String> result = new HashSet<>();

        if (values == null) return result;

        for (String value : values)
            if (value != null && !value.isBlank()) result.add(value.trim().toLowerCase(Locale.ROOT));

        return result;
    }
}

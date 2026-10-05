package com.yourcompany.testing.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;

final class StrictSchemaValidator {
    private StrictSchemaValidator() {}

    static void validate(JsonNode schema) {
        validateNode(schema, "$");
    }

    private static void validateNode(JsonNode node, String path) {
        if (!node.isObject()) throw new IllegalArgumentException(path + " must be a JSON schema object");

        String type = node.path("type").asText("");

        if (StringUtils.equals("object", type)) {
            if (!node.has("additionalProperties") || node.path("additionalProperties").asBoolean(true))
                throw new IllegalArgumentException(path + " requires additionalProperties=false for strict structured output");

            JsonNode properties = node.path("properties");
            JsonNode required = node.path("required");

            if (!properties.isObject() || !required.isArray()) throw new IllegalArgumentException(path + " requires properties and required");

            Iterator<String> fieldNames = properties.fieldNames();

            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                boolean requiredField = false;

                for (JsonNode requiredNode : required) {
                    if (fieldName.equals(requiredNode.asText())) {
                        requiredField = true;
                        break;
                    }
                }

                if (!requiredField) throw new IllegalArgumentException(path + "." + fieldName + " must be required in strict mode");

                validateNode(properties.get(fieldName), path + "." + fieldName);
            }
        } else if (StringUtils.equals("array", type)) {
            validateNode(node.path("items"), path + "[]");
        }
    }
}

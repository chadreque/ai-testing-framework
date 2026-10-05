package com.yourcompany.testing.infrastructure.jira;

import com.fasterxml.jackson.databind.JsonNode;

public final class JiraAdfParser {
    private JiraAdfParser() {}

    public static String toPlainText(JsonNode adf) {
        if (adf == null || adf.isMissingNode() || adf.isNull()) return "";
        if (adf.isTextual()) return normalize(adf.asText());

        StringBuilder result = new StringBuilder();
        appendNode(adf, result);
        return normalize(result.toString());
    }

    private static void appendNode(JsonNode node, StringBuilder result) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            result.append(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> appendNode(child, result));
            return;
        }
        if (!node.isObject()) return;

        String type = node.path("type").asText("");
        if ("hardBreak".equals(type)) {
            result.append('\n');
            return;
        }
        appendContent(node, result);
        if (java.util.Set.of("paragraph", "heading", "blockquote", "codeBlock", "listItem", "bulletList", "orderedList").contains(type)) result.append('\n');
        if (java.util.Set.of("tableCell", "tableHeader").contains(type)) result.append(" | ");
    }

    private static void appendContent(JsonNode node, StringBuilder result) {
        JsonNode content = node.path("content");
        if (content.isArray()) content.forEach(child -> appendNode(child, result));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\n{3,}", "\n\n").trim();
    }
}

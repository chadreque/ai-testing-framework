package com.yourcompany.testing.domain.catalog;

public record ComponentMetadata(
        String expectedTag,
        String expectedRole,
        String expectedText,
        String expectedLabel,
        String expectedType
) {
    public ComponentMetadata {
        expectedTag = safe(expectedTag);
        expectedRole = safe(expectedRole);
        expectedText = safe(expectedText);
        expectedLabel = safe(expectedLabel);
        expectedType = safe(expectedType);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

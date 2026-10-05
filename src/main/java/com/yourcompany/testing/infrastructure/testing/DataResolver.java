package com.yourcompany.testing.infrastructure.testing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DataResolver {
    private static final Pattern ENVIRONMENT_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private DataResolver() {}

    public static String resolve(String value) {
        if (value == null) return null;
        Matcher matcher = ENVIRONMENT_PLACEHOLDER.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String environmentValue = System.getenv(variableName);
            if (environmentValue == null) {
                throw new IllegalStateException("Required test environment variable is not configured: " + variableName);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(environmentValue));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}

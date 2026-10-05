package com.yourcompany.testing.infrastructure.privacy;

import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.application.port.TestDataPrivacySanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestDataSanitizer implements TestDataPrivacySanitizer {
    private final List<String> sensitiveFields;
    private final List<String> redactedTerms;
    private final List<Pattern> redactedPatterns;

    public TestDataSanitizer(ApplicationConfiguration.TestDataPrivacyConfig configuration) {
        this.sensitiveFields = normalize(configuration.sensitiveFields);
        this.redactedTerms = normalize(configuration.redactedTerms);
        this.redactedPatterns = configuration.redactedPatterns == null
                ? List.of()
                : configuration.redactedPatterns.stream().filter(value -> value != null && !value.isBlank()).map(Pattern::compile).toList();
    }

    public String sanitize(String input) {
        if (input == null) return "";
        String sanitized = input;

        for (String field : sensitiveFields) {
            String placeholder = placeholderFor(field);
            Pattern assignmentPattern = Pattern.compile(
                    "(?i)(\\b" + Pattern.quote(field) + "\\b\\s*[:=]\\s*)([\\\"']?)([^\\r\\n,;\\\"']+)([\\\"']?)");
            Matcher assignmentMatcher = assignmentPattern.matcher(sanitized);
            sanitized = assignmentMatcher.replaceAll(match -> Matcher.quoteReplacement(match.group(1) + placeholder));

            Pattern jsonPattern = Pattern.compile("(?i)([\\\"']" + Pattern.quote(field) + "[\\\"']\\s*:\\s*[\\\"'])(.*?)([\\\"'])");
            sanitized = jsonPattern.matcher(sanitized).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + placeholder + match.group(3)));

            Pattern queryPattern = Pattern.compile("(?i)([?&]" + Pattern.quote(field) + "=)[^&#\\s]+", Pattern.CASE_INSENSITIVE);
            sanitized = queryPattern.matcher(sanitized).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + placeholder));
        }

        sanitized = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+").matcher(sanitized)
                .replaceAll(Matcher.quoteReplacement("Bearer ${TEST_TOKEN}"));

        for (String term : redactedTerms) {
            if (!term.isBlank()) sanitized = sanitized.replaceAll("(?i)" + Pattern.quote(term), "[REDACTED]");
        }
        for (Pattern pattern : redactedPatterns) sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
        return sanitized;
    }

    private static List<String> normalize(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) if (value != null && !value.isBlank()) result.add(value.trim().toLowerCase(Locale.ROOT));
        return result;
    }

    private static String placeholderFor(String field) {
        String normalized = field.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (normalized.contains("PASSWORD") || normalized.equals("PWD") || normalized.equals("PASSWD")) return "${TEST_PASSWORD}";
        if (normalized.contains("USERNAME") || normalized.equals("USER_NAME") || normalized.equals("LOGIN")) return "${TEST_USERNAME}";
        if (normalized.equals("EMAIL")) return "${TEST_EMAIL}";
        if (normalized.contains("TOKEN") || normalized.equals("AUTHORIZATION")) return "${TEST_TOKEN}";
        if (normalized.contains("API_KEY") || normalized.equals("APIKEY")) return "${TEST_API_KEY}";
        if (normalized.contains("COOKIE")) return "${TEST_COOKIE}";
        if (normalized.contains("COMPANY")) return "${TEST_COMPANY}";
        return "${TEST_SECRET}";
    }
}

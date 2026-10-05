package com.yourcompany.testing.application.generation;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageUrlExtractor {
    private static final Pattern LABELED_URL = Pattern.compile("(?im)(?:page\\s*url|url\\s*(?:da|de)\\s*p[aá]gina|page|p[aá]gina)\\s*[:=-]\\s*(https?://[^\\s<>\\\"']+)");

    public Optional<String> extract(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) return Optional.empty();

        Matcher labeledMatcher = LABELED_URL.matcher(sourceText);

        if (labeledMatcher.find()) return Optional.of(trimTrailingPunctuation(labeledMatcher.group(1)));

        return Optional.empty();
    }

    private String trimTrailingPunctuation(String value) {
        return StringUtils.replace(value, "[),.;]+$", ""); //value.replaceAll("[),.;]+$", "");
    }
}

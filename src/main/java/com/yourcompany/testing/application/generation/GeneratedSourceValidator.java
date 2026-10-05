package com.yourcompany.testing.application.generation;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class GeneratedSourceValidator {
    private static final List<Pattern> FORBIDDEN_SOURCE = List.of(
            Pattern.compile("\\bdriver\\s*\\.\\s*findElement\\b"),
            Pattern.compile("\\bBy\\s*\\."),
            Pattern.compile("\\bWebDriver\\b"),
            Pattern.compile("\\bWebElement\\b"),
            Pattern.compile("Thread\\s*\\.\\s*sleep"),
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
            Pattern.compile("(?i)(password|passwd|pwd|api[_-]?key|token|secret)\\s*=\\s*\"(?!\\$\\{)[^\"]+\""),
            Pattern.compile("\"\\s*//[^\"]+\""),
            Pattern.compile("(?i)\"\\s*(css|xpath)\\s*[:=][^\"]*\"")
    );

    private static final Set<String> MEANINGLESS_PARAMETER_NAMES = Set.of("x", "s", "str", "obj", "object", "arg", "args", "value1", "value2", "param", "parameter");

    public void validateSource(String javaSource) {
        if (javaSource == null || javaSource.isBlank())
            throw new IllegalArgumentException("Generated Java source is empty");

        for (Pattern forbiddenPattern : FORBIDDEN_SOURCE) {
            if (forbiddenPattern.matcher(javaSource).find())
                throw new IllegalArgumentException("Generated step source violates the framework rule: " + forbiddenPattern.pattern());
        }
    }

    public void validateParameterName(String parameterName) {
        if (parameterName == null || !parameterName.matches("[A-Za-z_$][A-Za-z0-9_$]*"))
            throw new IllegalArgumentException("Invalid generated Java parameter name: " + parameterName);

        if (MEANINGLESS_PARAMETER_NAMES.contains(parameterName.toLowerCase()))
            throw new IllegalArgumentException("Generated parameter name is not meaningful: " + parameterName);
    }
}

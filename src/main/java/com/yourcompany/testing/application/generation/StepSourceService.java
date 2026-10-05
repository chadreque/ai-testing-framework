package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.domain.testcase.StepClassModel;
import com.yourcompany.testing.domain.testcase.StepDefinitionModel;
import com.yourcompany.testing.domain.testcase.StepGenerationResult;
import com.yourcompany.testing.domain.testcase.StepParameter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StepSourceService {
    private static final Set<String> KEYWORDS = Set.of("Given", "When", "Then", "And", "But");

    private static final Pattern CUCUMBER_PARAMETER = Pattern.compile("\\{(string|int|float|double|word|boolean)\\}");
    private static final Pattern ENVIRONMENT_PLACEHOLDER_IN_EXPRESSION = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*}");
    private static final Pattern EXISTING_EXPRESSION = Pattern.compile("@(Given|When|Then|And|But)\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)");

    private final GeneratedSourceValidator validator;
    private final String basePackage;

    public StepSourceService(GeneratedSourceValidator validator, String basePackage) {
        this.validator = validator;
        this.basePackage = basePackage;
    }

    public List<PendingJavaSource> render(StepGenerationResult result, Path outputDirectory, String packageName) {
        if (result == null || result.classes().isEmpty()) return List.of();

        validatePackageName(packageName);

        Map<Path, PendingJavaSource> pendingByPath = new LinkedHashMap<>();

        for (StepClassModel stepClass : result.classes()) {
            String className = requireJavaIdentifier(stepClass.className(), "Generated step class name");

            Path filePath = outputDirectory.resolve(className + ".java");

            String existingSource = readIfExists(filePath);
            String mergedSource = existingSource == null
                    ? renderNewClass(packageName, className, stepClass.steps())
                    : mergeIntoExistingClass(existingSource, stepClass.steps());

            if (StringUtils.isBlank(mergedSource)) continue;
            if (StringUtils.isNotBlank(existingSource) && existingSource.equals(mergedSource)) continue;

            validator.validateSource(mergedSource);
            pendingByPath.put(filePath, new PendingJavaSource(filePath, mergedSource));
        }
        return List.copyOf(pendingByPath.values());
    }

    public List<Path> write(List<PendingJavaSource> pendingSources) {
        List<Path> writtenFiles = new ArrayList<>();

        for (PendingJavaSource pendingSource : pendingSources) {
            try {
                Files.createDirectories(pendingSource.path().getParent());
                Files.writeString(pendingSource.path(), pendingSource.source());

                writtenFiles.add(pendingSource.path());
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to write generated step source: " + pendingSource.path(), exception);
            }
        }
        return writtenFiles;
    }

    private String requireBasePackage(String basePackage) {
        if (basePackage == null || basePackage.isBlank())
            throw new IllegalArgumentException("Application base package is required");

        if (!basePackage.matches("[A-Za-z_$][A-Za-z0-9_$]*" + "(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new IllegalArgumentException("Invalid application base package: " + basePackage);

        return basePackage.trim();
    }

    private String renderNewClass(String packageName, String className, List<StepDefinitionModel> steps) {
        List<StepDefinitionModel> uniqueSteps = uniqueMissingSteps("", steps);

        if (uniqueSteps.isEmpty()) return "";

        StringBuilder source = new StringBuilder("package ").append(packageName).append(";\n\n");

        appendImports(source, uniqueSteps);

        source.append("\npublic class ")
                .append(className)
                .append(" {\n\n")
                .append("    private PageActions pageActions() {\n")
                .append("        return TestRuntime.current().pageActions();\n")
                .append("    }\n\n");

        for (StepDefinitionModel step : uniqueSteps) source.append(renderMethod(step));

        source.append("}\n");
        return source.toString();
    }

    private String mergeIntoExistingClass(String existingSource, List<StepDefinitionModel> proposedSteps) {
        List<StepDefinitionModel> missingSteps = uniqueMissingSteps(existingSource, proposedSteps);

        if (missingSteps.isEmpty()) return existingSource;

        String withImports = ensureImports(existingSource, missingSteps);

        int classEnd = withImports.lastIndexOf('}');

        if (classEnd < 0) throw new IllegalArgumentException("Existing generated step class has no closing brace");

        StringBuilder additions = new StringBuilder();

        for (StepDefinitionModel step : missingSteps) additions.append('\n').append(renderMethod(step));

        return withImports.substring(0, classEnd).stripTrailing() + "\n" + additions + "}\n";
    }

    private List<StepDefinitionModel> uniqueMissingSteps(String existingSource, List<StepDefinitionModel> proposedSteps) {
        Set<String> existingExpressions = existingExpressions(existingSource);
        Set<String> acceptedExpressions = new HashSet<>(existingExpressions);

        List<StepDefinitionModel> result = new ArrayList<>();

        if (proposedSteps == null) return result;

        for (StepDefinitionModel step : proposedSteps) {
            validateStep(step);

            String expressionKey = normalizeExpression(step.expression());

            if (acceptedExpressions.add(expressionKey)) result.add(step);
        }

        return result;
    }

    private Set<String> existingExpressions(String source) {
        Set<String> expressions = new HashSet<>();

        Matcher matcher = EXISTING_EXPRESSION.matcher(source == null ? "" : source);

        while (matcher.find()) expressions.add(normalizeExpression(unescapeJava(matcher.group(2))));

        return expressions;
    }

    private void validateStep(StepDefinitionModel step) {
        if (step == null) throw new IllegalArgumentException("Generated step definition is null");
        if (!KEYWORDS.contains(step.keyword())) throw new IllegalArgumentException("Invalid generated Cucumber keyword: " + step.keyword());
        if (StringUtils.isBlank(step.expression())) throw new IllegalArgumentException("Generated step expression is empty");
        if (ENVIRONMENT_PLACEHOLDER_IN_EXPRESSION.matcher(step.expression()).find())
            throw new IllegalArgumentException(
                    "Generated Cucumber expression must not contain an environment placeholder such as ${TEST_PAGE_URL}. "
                            + "Use {word} for an unquoted placeholder or {string} for a quoted placeholder and pass the captured value to PageActions. Expression: "
                            + step.expression());

        requireJavaIdentifier(step.methodName(), "Generated step method name");

        List<String> parameterTypes = parameterTypes(step.expression());

        if (parameterTypes.size() != step.parameters().size())
            throw new IllegalArgumentException("Parameter count does not match Cucumber expression for: " + step.expression());

        Set<String> names = new HashSet<>();

        for (StepParameter parameter : step.parameters()) {
            validator.validateParameterName(parameter.name());

            if (!names.add(parameter.name())) throw new IllegalArgumentException("Duplicate generated parameter name: " + parameter.name());
        }

        if (step.body().isBlank()) throw new IllegalArgumentException("Generated step body is empty for: " + step.expression());
    }

    private String renderMethod(StepDefinitionModel step) {
        List<String> parameterTypes = parameterTypes(step.expression());

        StringBuilder method = new StringBuilder();
        method.append("    @").append(step.keyword()).append("(\"").append(escapeJava(step.expression())).append("\")\n");
        method.append("    public void ").append(step.methodName()).append('(');

        for (int index = 0; index < parameterTypes.size(); index++) {
            if (index > 0) method.append(", ");

            method.append(parameterTypes.get(index)).append(' ').append(step.parameters().get(index).name());
        }

        method.append(") {\n");


        for (String line : step.body().split("\\R", -1))
            method.append("        ").append(line).append('\n');

        method.append("    }\n\n");

        return method.toString();
    }

    private void appendImports(StringBuilder source, List<StepDefinitionModel> steps) {
        TreeSet<String> imports = requiredImports(steps);

        for (String importLine : imports) source.append(importLine).append('\n');
    }

    private String ensureImports(String source, List<StepDefinitionModel> steps) {
        TreeSet<String> requiredImports = requiredImports(steps);

        StringBuilder missingImports = new StringBuilder();

        for (String importLine : requiredImports) {
            if (!source.contains(importLine)) missingImports.append(importLine).append('\n');
        }

        if (missingImports.isEmpty()) return source;
        int packageEnd = source.indexOf(';');
        if (packageEnd < 0) throw new IllegalArgumentException("Existing step source has no package declaration");

        return source.substring(0, packageEnd + 1) + "\n\n" + missingImports + source.substring(packageEnd + 1).stripLeading();
    }

    private TreeSet<String> requiredImports(List<StepDefinitionModel> steps) {
        TreeSet<String> imports = new TreeSet<>();

        for (StepDefinitionModel step : steps)
            imports.add("import io.cucumber.java.en." + step.keyword() + ";");

        imports.add("import " + basePackage + ".infrastructure.runtime.TestRuntime;");
        imports.add("import " + basePackage + ".infrastructure.selenium.PageActions;");
        imports.add("import " + basePackage + ".infrastructure.testing.DataResolver;");
        imports.add("import org.junit.jupiter.api.Assertions;");

        return imports;
    }

    private List<String> parameterTypes(String expression) {
        List<String> types = new ArrayList<>();

        Matcher matcher = CUCUMBER_PARAMETER.matcher(expression);

        while (matcher.find()) {
            types.add(
                    switch (matcher.group(1)) {
                        case "int" -> "int";
                        case "float" -> "float";
                        case "double" -> "double";
                        case "boolean" -> "boolean";
                        case "string", "word" -> "String";
                        default -> throw new IllegalArgumentException("Unsupported Cucumber parameter type: " + matcher.group(1));
                    }
            );
        }
        return types;
    }

    private String readIfExists(Path filePath) {
        if (!Files.exists(filePath)) return null;

        try {
            return Files.readString(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read existing step source: " + filePath, exception);
        }
    }

    private String normalizeExpression(String expression) {
        return expression.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJava(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String requireJavaIdentifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*"))
            throw new IllegalArgumentException(label + " is invalid: " + value);

        return value;
    }

    private void validatePackageName(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new IllegalArgumentException("Generated step package is invalid: " + packageName);
    }

    public record PendingJavaSource(Path path, String source) {}
}

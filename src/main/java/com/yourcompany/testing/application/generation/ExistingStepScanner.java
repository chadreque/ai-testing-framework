package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.domain.testcase.ExistingStep;
import com.yourcompany.testing.domain.testcase.StepCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExistingStepScanner {
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern STEP_PATTERN = Pattern.compile("@(Given|When|Then|And|But)\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)\\s*(?:\\R|.)*?public\\s+void\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(", Pattern.MULTILINE);

    public StepCatalog scan(Path sourceRoot) {
        if (sourceRoot == null || !Files.exists(sourceRoot)) return new StepCatalog(List.of());

        List<ExistingStep> steps = new ArrayList<>();

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java")).forEach(path -> scanFile(path, steps));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan existing Cucumber steps under: " + sourceRoot, exception);
        }

        return new StepCatalog(steps);
    }

    private void scanFile(Path path, List<ExistingStep> target) {
        try {
            String source = Files.readString(path);

            Matcher classMatcher = CLASS_PATTERN.matcher(source);

            String className = classMatcher.find() ? classMatcher.group(1) : path.getFileName().toString().replace(".java", "");

            Matcher stepMatcher = STEP_PATTERN.matcher(source);

            while (stepMatcher.find()) {
                target.add(new ExistingStep(stepMatcher.group(1), unescapeJava(stepMatcher.group(2)), className, stepMatcher.group(3)));
            }

        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read existing step source: " + path, exception);
        }
    }

    private String unescapeJava(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.GherkinStep;
import com.yourcompany.testing.domain.testcase.ScenarioModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeatureFileService {
    private static final Pattern SCENARIO_HEADER = Pattern.compile("(?m)^\\s*Scenario(?: Outline)?:\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATED_ID = Pattern.compile("(?m)^\\s*#\\s*@generated-id:\\s*([a-f0-9]{12,64})\\s*$", Pattern.CASE_INSENSITIVE);

    public FeatureWriteResult writeOrMerge(FeatureModel feature, Path featureDirectory) {
        return writeOrMerge(feature, featureDirectory, feature.featureName(), List.of());
    }

    public FeatureWriteResult writeOrMerge(FeatureModel feature, Path featureDirectory, String stableFeatureIdentity, List<String> scenarioIdentityKeys) {
        try {
            Files.createDirectories(featureDirectory);

            Path featureFile = featureDirectory.resolve(featureFileName(stableFeatureIdentity));

            if (!Files.exists(featureFile)) {
                Files.writeString(featureFile, renderFeature(feature, scenarioIdentityKeys));
                return new FeatureWriteResult(featureFile, feature.scenarios().size(), 0);
            }

            String existingText = Files.readString(featureFile);

            ExistingScenarios existingScenarios = parseExistingScenarios(existingText);

            StringBuilder merged = new StringBuilder(existingText.stripTrailing());

            int added = 0;
            int skipped = 0;

            for (int scenarioIndex = 0; scenarioIndex < feature.scenarios().size(); scenarioIndex++) {
                ScenarioModel scenario = feature.scenarios().get(scenarioIndex);

                String generatedId = generatedIdAt(scenarioIdentityKeys, scenarioIndex);
                String normalizedName = normalize(scenario.name());
                String stepFingerprint = stepFingerprint(scenario);

                if ((!generatedId.isBlank() && existingScenarios.generatedIds.contains(generatedId)) || existingScenarios.names.contains(normalizedName) || existingScenarios.stepFingerprints.contains(stepFingerprint)) {
                    skipped++;
                    continue;
                }

                merged.append(System.lineSeparator()).append(System.lineSeparator()).append(renderScenario(scenario, generatedId).stripTrailing());

                if (!generatedId.isBlank()) existingScenarios.generatedIds.add(generatedId);

                existingScenarios.names.add(normalizedName);
                existingScenarios.stepFingerprints.add(stepFingerprint);
                added++;
            }

            if (added > 0) Files.writeString(featureFile, merged.append(System.lineSeparator()).toString());

            return new FeatureWriteResult(featureFile, added, skipped);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write or merge generated Cucumber feature", exception);
        }
    }

    public String renderFeature(FeatureModel feature) {
        return renderFeature(feature, List.of());
    }

    private String renderFeature(FeatureModel feature, java.util.List<String> scenarioIdentityKeys) {
        StringBuilder featureText = new StringBuilder("Feature: ").append(cleanLine(feature.featureName())).append("\n\n");

        for (int scenarioIndex = 0; scenarioIndex < feature.scenarios().size(); scenarioIndex++) {
            featureText.append(renderScenario(feature.scenarios().get(scenarioIndex), generatedIdAt(scenarioIdentityKeys, scenarioIndex))).append('\n');
        }

        return featureText.toString();
    }

    private String renderScenario(ScenarioModel scenario, String generatedId) {
        StringBuilder scenarioText = new StringBuilder();

        if (!generatedId.isBlank()) scenarioText.append("  # @generated-id: ").append(generatedId).append('\n');

        scenarioText.append("  Scenario: ").append(cleanLine(scenario.name())).append('\n');

        for (GherkinStep step : scenario.steps()) {
            scenarioText.append("    ").append(step.keyword()).append(' ').append(cleanLine(step.text())).append('\n');
        }

        return scenarioText.toString();
    }

    private ExistingScenarios parseExistingScenarios(String featureText) {
        Set<String> names = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        Set<String> generatedIds = new HashSet<>();

        Matcher generatedIdMatcher = GENERATED_ID.matcher(featureText);

        while (generatedIdMatcher.find()) generatedIds.add(generatedIdMatcher.group(1).toLowerCase(Locale.ROOT));

        List<ScenarioHeader> headers = new java.util.ArrayList<>();

        Matcher matcher = SCENARIO_HEADER.matcher(featureText);

        while (matcher.find()) {
            headers.add(new ScenarioHeader(matcher.group(1), matcher.end(), matcher.start()));
        }

        for (int index = 0; index < headers.size(); index++) {
            ScenarioHeader header = headers.get(index);

            int blockEnd = index + 1 < headers.size() ? headers.get(index + 1).headerStart() : featureText.length();

            String block = featureText.substring(header.contentStart(), blockEnd);

            names.add(normalize(header.name()));

            fingerprints.add(normalizeStepBlock(block));
        }

        return new ExistingScenarios(names, fingerprints, generatedIds);
    }

    private String generatedIdAt(java.util.List<String> scenarioIdentityKeys, int scenarioIndex) {
        if (scenarioIdentityKeys == null || scenarioIndex >= scenarioIdentityKeys.size()) return "";

        String identity = scenarioIdentityKeys.get(scenarioIndex);

        return identity == null || identity.isBlank() ? "" : shortHash(identity);
    }

    private String stepFingerprint(ScenarioModel scenario) {
        StringBuilder steps = new StringBuilder();

        for (GherkinStep step : scenario.steps())
            steps.append(step.keyword()).append(' ').append(step.text()).append('\n');

        return normalizeStepBlock(steps.toString());
    }

    private String normalizeStepBlock(String block) {
        return block.lines().map(String::trim).filter(line -> line.matches("(?i)^(Given|When|Then|And|But)\\s+.+")).map(this::normalize).reduce("", (left, right) -> left + "\n" + right);
    }

    private String featureFileName(String featureName) {
        String ascii = Normalizer.normalize(featureName, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");

        if (slug.isBlank()) slug = "feature-" + shortHash(featureName);

        return slug + ".feature";
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build deterministic feature name", exception);
        }
    }

    private String cleanLine(String value) {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException("Generated Gherkin text must not be blank");

        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record ScenarioHeader(String name, int contentStart, int headerStart) {
    }

    private record ExistingScenarios(Set<String> names, Set<String> stepFingerprints, Set<String> generatedIds) {
    }

    public record FeatureWriteResult(Path path, int addedScenarios, int skippedScenarios) {
    }
}

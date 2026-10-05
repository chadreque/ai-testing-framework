package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.GherkinStep;
import com.yourcompany.testing.domain.testcase.ScenarioModel;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public final class FeatureValidator {
    private static final Set<String> KEYWORDS = Set.of("Given", "When", "Then", "And", "But");

    public void validate(FeatureModel feature) {
        if (feature == null) throw new IllegalArgumentException("Generated feature is null");
        if (StringUtils.isBlank(feature.featureName())) throw new IllegalArgumentException("Generated feature name is empty");
        if (feature.scenarios().isEmpty()) throw new IllegalArgumentException("Generated feature contains no scenarios");

        for (ScenarioModel scenario : feature.scenarios()) {
            if (StringUtils.isBlank(scenario.name())) throw new IllegalArgumentException("Generated scenario has no name");
            if (scenario.steps().isEmpty()) throw new IllegalArgumentException("Scenario contains no steps: " + scenario.name());

            for (GherkinStep step : scenario.steps()) {
                if (!KEYWORDS.contains(step.keyword())) throw new IllegalArgumentException("Invalid Gherkin keyword in scenario '" + scenario.name() + "': " + step.keyword());
                if (StringUtils.isBlank(step.text())) throw new IllegalArgumentException("Empty Gherkin step in scenario: " + scenario.name());
            }
        }
    }
}

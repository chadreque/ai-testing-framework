package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record FeatureModel(String featureName, List<ScenarioModel> scenarios) {
    public FeatureModel {
        featureName = featureName == null ? "" : featureName.trim();
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
    }
}

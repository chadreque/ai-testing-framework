package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record ScenarioModel(String name, List<GherkinStep> steps) {
    public ScenarioModel {
        name = name == null ? "" : name.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

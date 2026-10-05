package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record StepClassModel(String className, List<StepDefinitionModel> steps) {
    public StepClassModel {
        className = className == null ? "" : className.trim();
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

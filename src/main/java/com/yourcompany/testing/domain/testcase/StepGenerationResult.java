package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record StepGenerationResult(List<StepClassModel> classes) {
    public StepGenerationResult {
        classes = classes == null ? List.of() : List.copyOf(classes);
    }
}

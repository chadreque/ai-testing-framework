package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record StepCatalog(List<ExistingStep> steps) {
    public StepCatalog {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

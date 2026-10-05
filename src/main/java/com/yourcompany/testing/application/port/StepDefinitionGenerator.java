package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.testcase.StepCatalog;
import com.yourcompany.testing.domain.testcase.StepGenerationResult;

import java.util.Optional;

public interface StepDefinitionGenerator {
    StepGenerationResult generate(String featureText, StepCatalog existingSteps, Optional<ComponentCatalog> componentCatalog, String validationFeedback);
}

package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record StepDefinitionModel(
        String keyword,
        String expression,
        String methodName,
        List<StepParameter> parameters,
        String body
) {
    public StepDefinitionModel {
        keyword = keyword == null ? "" : keyword.trim();
        expression = expression == null ? "" : expression.trim();
        methodName = methodName == null ? "" : methodName.trim();
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        body = body == null ? "" : body.trim();
    }
}

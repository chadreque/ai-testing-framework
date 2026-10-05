package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record TestCase(
        String id,
        String title,
        String description,
        List<String> preconditions,
        List<TestStep> steps,
        List<String> expectedResults,
        List<TestDataItem> data
) {
    public TestCase {
        id = safe(id);
        title = safe(title);
        description = safe(description);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        steps = steps == null ? List.of() : List.copyOf(steps);
        expectedResults = expectedResults == null ? List.of() : List.copyOf(expectedResults);
        data = data == null ? List.of() : List.copyOf(data);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

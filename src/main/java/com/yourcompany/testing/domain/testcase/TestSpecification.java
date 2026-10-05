package com.yourcompany.testing.domain.testcase;

import java.util.List;

public record TestSpecification(String title, String pageUrl, List<TestCase> testCases) {
    public TestSpecification {
        title = title == null ? "" : title;
        pageUrl = pageUrl == null ? "" : pageUrl;
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }

    public TestSpecification withPageUrl(String resolvedPageUrl) {
        return new TestSpecification(title, resolvedPageUrl == null ? "" : resolvedPageUrl, testCases);
    }
}

package com.yourcompany.testing.domain.source;

import org.apache.commons.lang3.StringUtils;

public record TestSource(TestSourceType type, String value) {
    public TestSource {
        if (type == null) throw new IllegalArgumentException("Test source type is required");
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException("Test source value is required");

        value = StringUtils.trim(value);
    }

    public static TestSource file(String path) {
        return new TestSource(TestSourceType.FILE, path);
    }

    public static TestSource jira(String issueKey) {
        return new TestSource(TestSourceType.JIRA, issueKey);
    }
}

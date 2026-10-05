package com.yourcompany.testing.domain.source;

import org.apache.commons.lang3.StringUtils;

public record TestSourceContent(String description, String content) {
    public TestSourceContent {
        description = description == null ? "source" : description;

        if (StringUtils.isBlank(content)) throw new IllegalArgumentException("Test source content is empty: " + description);
    }
}

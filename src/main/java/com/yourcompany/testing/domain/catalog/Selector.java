package com.yourcompany.testing.domain.catalog;

import org.apache.commons.lang3.StringUtils;

public record Selector(
        SelectorStrategy strategy,
        String value,
        double confidence,
        String reason
) {
    public Selector {
        if (strategy == null) throw new IllegalArgumentException("Selector strategy is required");
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException("Selector value is required");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("Selector confidence must be between 0 and 1");

        reason = reason == null ? "" : reason;
    }
}

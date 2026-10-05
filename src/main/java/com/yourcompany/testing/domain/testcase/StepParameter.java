package com.yourcompany.testing.domain.testcase;

import org.apache.commons.lang3.StringUtils;

public record StepParameter(String name) {
    public StepParameter {
        if (StringUtils.isBlank(name)) throw new IllegalArgumentException("Step parameter name is required");
        name = name.trim();
    }
}

package com.yourcompany.testing.domain.testcase;

public record GherkinStep(String keyword, String text) {
    public GherkinStep {
        keyword = keyword == null ? "" : keyword.trim();
        text = text == null ? "" : text.trim();
    }
}

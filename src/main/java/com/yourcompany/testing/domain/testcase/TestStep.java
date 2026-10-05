package com.yourcompany.testing.domain.testcase;

public record TestStep(int order, String action, String data, String expectedResult) {
    public TestStep {
        action = action == null ? "" : action;
        data = data == null ? "" : data;
        expectedResult = expectedResult == null ? "" : expectedResult;
    }
}

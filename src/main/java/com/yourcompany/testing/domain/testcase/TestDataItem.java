package com.yourcompany.testing.domain.testcase;

public record TestDataItem(String key, String value) {
    public TestDataItem {
        key = key == null ? "" : key;
        value = value == null ? "" : value;
    }
}

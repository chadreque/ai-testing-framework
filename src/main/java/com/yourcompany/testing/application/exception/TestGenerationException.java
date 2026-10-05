package com.yourcompany.testing.application.exception;

public class TestGenerationException extends RuntimeException {
    public TestGenerationException(String message) { super(message); }
    public TestGenerationException(String message, Throwable cause) { super(message, cause); }
}

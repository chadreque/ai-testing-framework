package com.yourcompany.testing.application.exception;

public class SourceReadException extends RuntimeException {
    public SourceReadException(String message) { super(message); }
    public SourceReadException(String message, Throwable cause) { super(message, cause); }
}

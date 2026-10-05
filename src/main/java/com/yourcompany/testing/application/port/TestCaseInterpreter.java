package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.testcase.TestSpecification;

public interface TestCaseInterpreter {
    TestSpecification interpret(String sanitizedHumanTestSpecification);
}

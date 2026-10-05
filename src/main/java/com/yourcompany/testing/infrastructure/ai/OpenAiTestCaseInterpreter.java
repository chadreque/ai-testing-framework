package com.yourcompany.testing.infrastructure.ai;

import com.yourcompany.testing.application.port.TestCaseInterpreter;
import com.yourcompany.testing.domain.testcase.TestSpecification;

public final class OpenAiTestCaseInterpreter implements TestCaseInterpreter {
    private final OpenAiClient client;

    public OpenAiTestCaseInterpreter(OpenAiClient client) {
        this.client = client;
    }

    @Override
    public TestSpecification interpret(String sanitizedHumanTestSpecification) {
        String instructions = """
                You are a senior QA analyst. Convert the supplied human-readable test specification into the canonical JSON structure.
                The input may contain one or many test cases. Preserve every distinct test case and its business intent.
                Extract a page URL only when it is explicitly present in the source. If no page URL is present, return an empty string.
                Do not invent URLs, requirements, credentials, test data, or expected results.
                Preserve ambiguities rather than guessing. Keep ordered actions ordered.
                Sensitive values in the input may already be replaced by ${TEST_*} placeholders; preserve those placeholders exactly.
                Return only data matching the supplied JSON schema.
                """;

        return client.generateStructured(instructions, sanitizedHumanTestSpecification, "test_specification", OpenAiSchemaFactory.testSpecification(client.mapper()), TestSpecification.class);
    }
}

package com.yourcompany.testing.infrastructure.ai;

import com.yourcompany.testing.application.port.FeatureGenerator;
import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.TestSpecification;
import org.apache.commons.lang3.StringUtils;

public final class OpenAiFeatureGenerator implements FeatureGenerator {
    private final OpenAiClient client;

    public OpenAiFeatureGenerator(OpenAiClient client) {
        this.client = client;
    }

    @Override
    public FeatureModel generate(TestSpecification testSpecification) {
        try {
            TestSpecification aiSpecification = StringUtils.isBlank(testSpecification.pageUrl())
                    ? testSpecification
                    : testSpecification.withPageUrl("${TEST_PAGE_URL}");

            String input = client.mapper().writeValueAsString(aiSpecification);
            String instructions = """
                    You are a senior BDD architect. Convert the canonical test specification into production Cucumber Gherkin data.
                    Preserve all distinct test cases as scenarios. Preserve business meaning and expected results.
                    Use only Given, When, Then, And, or But. Prefer reusable semantic wording.
                    Keep ${TEST_*} placeholders exactly as provided. Never expose or invent credentials or secrets.
                    When a ${TEST_*} placeholder is used as a value in a Gherkin step, prefer placing the placeholder inside double quotes so a reusable {string} Cucumber expression can capture it.
                    Do not generate Java code, Selenium selectors, CSS, XPath, or locator details.
                    Do not invent a URL that is absent from the canonical specification. If pageUrl is ${TEST_PAGE_URL}, preserve that placeholder in navigation steps.
                    Cucumber scenarios are independent. When pageUrl is present and a scenario performs UI interactions or assertions, include navigation to "${TEST_PAGE_URL}" in that scenario before the first UI operation unless the scenario already contains explicit navigation.
                    Return only data matching the supplied JSON schema.
                    """;

            return client.generateStructured(instructions, input, "cucumber_feature", OpenAiSchemaFactory.feature(client.mapper()), FeatureModel.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate Cucumber feature model", exception);
        }
    }
}

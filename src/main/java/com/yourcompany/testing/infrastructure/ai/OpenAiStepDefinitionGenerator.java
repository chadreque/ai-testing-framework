package com.yourcompany.testing.infrastructure.ai;

import com.yourcompany.testing.application.port.StepDefinitionGenerator;
import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.ComponentDefinition;
import com.yourcompany.testing.domain.testcase.StepCatalog;
import com.yourcompany.testing.domain.testcase.StepGenerationResult;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OpenAiStepDefinitionGenerator implements StepDefinitionGenerator {
    private final OpenAiClient client;

    public OpenAiStepDefinitionGenerator(OpenAiClient client) {
        this.client = client;
    }

    @Override
    public StepGenerationResult generate(
            String featureText,
            StepCatalog existingSteps,
            Optional<ComponentCatalog> componentCatalog,
            String validationFeedback) {
        try {
            String catalogSummary = componentCatalog.map(this::catalogSummary).orElse("NO CATALOG AVAILABLE");
            String input = "FEATURE:\n"
                    + featureText
                    + "\n\nEXISTING STEP DEFINITIONS:\n"
                    + client.mapper().writeValueAsString(existingSteps)
                    + "\n\nAVAILABLE LOGICAL COMPONENTS (selectors intentionally omitted):\n"
                    + catalogSummary
                    + (StringUtils.isBlank(validationFeedback) ? "" : "\n\nPREVIOUS VALIDATION ERROR TO CORRECT:\n"
                    + validationFeedback);

            String instructions = """
                    You are a senior Java Cucumber automation engineer. Generate only step definitions that are missing from the existing step catalog.
                    Return complete method bodies as Java statements, but do not return package declarations, imports, class declarations, selectors, or WebDriver code.

                    Generated step bodies must use the framework contract only:
                    - pageActions().open(url)
                    - pageActions().type("logical.componentName", value)
                    - pageActions().click("logical.componentName")
                    - pageActions().text("logical.componentName")
                    - pageActions().isDisplayed("logical.componentName")
                    - pageActions().currentUrl()
                    - pageActions().waitForUrl(expectedUrl)
                    - DataResolver.resolve(value) when a non-pageActions operation needs an environment-backed value
                    - Assertions.assertEquals / assertTrue / assertFalse for assertions

                    PageActions resolves ${TEST_*} environment placeholders at runtime for open, type, and waitForUrl.
                    CRITICAL CUCUMBER EXPRESSION RULE: never copy a literal ${TEST_*} placeholder into a Cucumber annotation expression. Cucumber interprets text inside braces as a parameter type.
                    - If the feature step contains an unquoted placeholder such as ${TEST_PAGE_URL}, use {word} in the expression, add a meaningful String parameter such as pageUrl, and pass that parameter to pageActions().open(pageUrl).
                    - If the feature step contains a quoted placeholder such as "${TEST_PAGE_URL}", use {string}, add a meaningful String parameter, and pass that parameter to PageActions.
                    - The Java method body must receive the placeholder text through the Cucumber parameter; do not hard-code ${TEST_*} in the annotation expression.
                    Prefer logical component names from the supplied catalog summary. The catalog summary intentionally contains no selectors.
                    If no catalog is available and a UI interaction requires a component, infer a stable semantic logical name such as login.username; runtime discovery will resolve it from the current page.
                    Never use driver.findElement, WebDriver, WebElement, By.*, CSS selectors, XPath, raw HTML selectors, Thread.sleep, hard-coded credentials, cookies, API keys, or tokens.
                    Never place selectors in constants or strings.
                    Do not turn test-data/environment preconditions such as "the environment contains no clients" into arbitrary UI visibility checks. Only use pageActions().isDisplayed when the Gherkin step is actually asserting UI visibility/presence.
                    If a precondition requires external test-data setup that this framework does not expose, do not invent database/API/selector behavior; generate a clear assertion only when the feature itself describes an observable UI condition.
                    Do not invent URLs. Use URLs only when they appear in the feature text or are provided as Cucumber parameters.
                    Use meaningful Java method names and meaningful parameter names. Never use x, s, str, obj, arg1, value1, or similarly meaningless names.
                    The parameters array must contain exactly one meaningful name for each Cucumber expression placeholder, in placeholder order.
                    Reuse equivalent existing step definitions and return no duplicate expressions.
                    Keep classes grouped by functional area; do not create one class per step.
                    Return only data matching the supplied JSON schema.
                    """;

            return client.generateStructured(instructions, input, "cucumber_step_definitions", OpenAiSchemaFactory.stepGeneration(client.mapper()), StepGenerationResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate Java Cucumber step definitions", exception);
        }
    }

    private String catalogSummary(ComponentCatalog catalog) {
        List<Map<String, String>> components = catalog.components().values().stream()
                .map(this::componentSummary)
                .toList();
        try {
            return client.mapper().writeValueAsString(Map.of("pageName", catalog.pageName(), "components", components));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize catalog summary", exception);
        }
    }

    private Map<String, String> componentSummary(ComponentDefinition component) {
        return Map.of(
                "logicalName", component.logicalName(),
                "elementType", component.elementType(),
                "label", component.metadata().expectedLabel(),
                "type", component.metadata().expectedType());
    }
}

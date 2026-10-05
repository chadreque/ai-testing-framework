package com.yourcompany.testing.infrastructure.config;

import java.util.ArrayList;
import java.util.List;

public class ApplicationConfiguration {
    public ApplicationConfig application = new ApplicationConfig();

    public AiConfig ai = new AiConfig();
    public BrowserConfig browser = new BrowserConfig();
    public CatalogConfig catalog = new CatalogConfig();
    public GenerationConfig generation = new GenerationConfig();
    public JiraConfig jira = new JiraConfig();

    public static class ApplicationConfig {
        public String basePackage = "com.yourcompany.testing";
    }

    public static class AiConfig {
        public String provider = "openai";
        public boolean enabled = true;
        public String model = "gpt-5.6-luna";
        public String apiKeyEnvironmentVariable = "OPENAI_API_KEY";
        public int maxRetries = 3;
        public int timeoutSeconds = 180;
        public PrivacyConfig privacy = new PrivacyConfig();
    }

    public static class PrivacyConfig {
        public DomPrivacyConfig dom = new DomPrivacyConfig();
        public TestDataPrivacyConfig testData = new TestDataPrivacyConfig();
    }

    public static class DomPrivacyConfig {
        public List<String> allowedAttributes = new ArrayList<>(List.of(
                "id", "name", "type",
                "placeholder", "aria-label", "aria-labelledby",
                "role", "title", "class", "data-testid",
                "data-test", "data-qa", "disabled",
                "scope", "for", "contenteditable")
        );

        public List<String> excludedAttributes = new ArrayList<>(List.of("value", "src", "srcset"));
        public List<String> excludedElementHints = new ArrayList<>(List.of("company-name", "company-logo", "logo", "brand"));
        public List<String> redactedTerms = new ArrayList<>();
        public List<String> redactedPatterns = new ArrayList<>();
    }

    public static class TestDataPrivacyConfig {
        public List<String> sensitiveFields = new ArrayList<>(List.of(
                "password", "passwd", "pwd", "api-key", "apikey", "api_key", "token",
                "access-token", "refresh-token", "secret", "client-secret", "authorization",
                "cookie", "username", "user-name", "login", "email", "company-name", "company"));
        public List<String> redactedTerms = new ArrayList<>();
        public List<String> redactedPatterns = new ArrayList<>();
    }

    public static class BrowserConfig {
        public String type = "chrome";
        public boolean headless = false;
        public int pageLoadTimeoutSeconds = 60;
        public int waitTimeoutSeconds = 30;
        public int domStabilityTimeoutSeconds = 15;
        public long domQuietPeriodMillis = 750;
        public long domMinimumObservationMillis = 1200;
        public int domMaximumElements = 4000;
        public boolean autoNavigateToDefaultPage = true;
        public String defaultPageUrlEnvironmentVariable = "TEST_PAGE_URL";
    }

    public static class CatalogConfig {
        public String directory = "catalogs";
        public boolean refreshComponentOnSelectorFailure = true;
        public double minimumSelectorConfidence = 0.50;
    }

    public static class GenerationConfig {
        public String featureOutputDirectory = "src/test/resources/features";
        public String stepOutputDirectory = "src/test/java";
        public int maxStepRepairAttempts = 2;
    }

    public static class JiraConfig {
        public String baseUrlEnvironmentVariable = "JIRA_BASE_URL";
        public String emailEnvironmentVariable = "JIRA_EMAIL";
        public String apiTokenEnvironmentVariable = "JIRA_API_TOKEN";
        public int connectTimeoutSeconds = 10;
        public int requestTimeoutSeconds = 30;
        public int maxRetries = 3;
    }
}

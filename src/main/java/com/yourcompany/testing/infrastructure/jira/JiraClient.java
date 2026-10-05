package com.yourcompany.testing.infrastructure.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.infrastructure.config.ConfigurationLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public final class JiraClient {
    private final ApplicationConfiguration.JiraConfig configuration;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraClient(ApplicationConfiguration.JiraConfig configuration) {
        this.configuration = configuration;
    }

    public JiraIssue getIssue(String issueKey) {
        validateIssueKey(issueKey);
        String baseUrl = normalizeBaseUrl(ConfigurationLoader.requiredEnvironmentVariable(configuration.baseUrlEnvironmentVariable));
        String email = ConfigurationLoader.requiredEnvironmentVariable(configuration.emailEnvironmentVariable);
        String token = ConfigurationLoader.requiredEnvironmentVariable(configuration.apiTokenEnvironmentVariable);
        String authorization = "Basic " + Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(configuration.connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        String encodedKey = URLEncoder.encode(issueKey, StandardCharsets.UTF_8);
        URI requestUri = URI.create(baseUrl + "/rest/api/3/issue/" + encodedKey + "?fields=summary,description,issuetype,status,project,labels");
        int attempt = 0;
        while (true) {
            try {
                HttpRequest request = HttpRequest.newBuilder(requestUri)
                        .timeout(Duration.ofSeconds(configuration.requestTimeoutSeconds))
                        .header("Authorization", authorization)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) return parseIssue(mapper.readTree(response.body()), baseUrl);
                if (isRetryable(response.statusCode()) && attempt < configuration.maxRetries) {
                    sleep(attempt++, response.headers().firstValue("Retry-After").orElse(null));
                    continue;
                }
                throw new IllegalStateException("Jira API request failed with HTTP " + response.statusCode());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Jira request was interrupted", exception);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                if (attempt < configuration.maxRetries) {
                    try {
                        sleep(attempt++, null);
                        continue;
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Jira retry wait was interrupted", interruptedException);
                    }
                }
                throw new IllegalStateException("Unable to read Jira issue: " + issueKey, exception);
            }
        }
    }

    private JiraIssue parseIssue(JsonNode root, String baseUrl) {
        JsonNode fields = root.path("fields");
        String key = text(root.path("key"));
        return new JiraIssue(
                key,
                text(fields.path("summary")),
                JiraAdfParser.toPlainText(fields.path("description")),
                text(fields.path("issuetype").path("name")),
                text(fields.path("status").path("name")),
                text(fields.path("project").path("key")),
                text(fields.path("project").path("name")),
                baseUrl + "/browse/" + key);
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleep(int attempt, String retryAfterHeader) throws InterruptedException {
        long seconds = Math.min(1L << Math.min(attempt, 3), 8L);
        if (retryAfterHeader != null) {
            try { seconds = Math.min(Long.parseLong(retryAfterHeader), 30L); } catch (NumberFormatException ignored) { }
        }
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }

    private String normalizeBaseUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("JIRA base URL must use HTTPS");
        return baseUrl.replaceAll("/+$", "");
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private void validateIssueKey(String issueKey) {
        if (issueKey == null || !issueKey.matches("[A-Za-z][A-Za-z0-9_]*-[0-9]+")) throw new IllegalArgumentException("Invalid Jira issue key: " + issueKey);
    }

    public record JiraIssue(
            String key,
            String summary,
            String description,
            String issueType,
            String status,
            String projectKey,
            String projectName,
            String url) {}
}

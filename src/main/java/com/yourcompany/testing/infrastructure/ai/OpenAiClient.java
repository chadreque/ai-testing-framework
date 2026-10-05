package com.yourcompany.testing.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.infrastructure.config.ConfigurationLoader;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class OpenAiClient {
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");

    private final ApplicationConfiguration.AiConfig configuration;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiClient(ApplicationConfiguration.AiConfig configuration) {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.mapper = new ObjectMapper();
    }

    public <T> T generateStructured(String instructions, String input, String schemaName, JsonNode schema, Class<T> responseType) {
        if (!configuration.enabled) throw new IllegalStateException("AI is disabled by configuration");
        if (!StringUtils.equalsIgnoreCase("openai", configuration.provider)) throw new IllegalStateException("Unsupported AI provider: " + configuration.provider);
        if (StringUtils.isBlank(instructions)) throw new IllegalArgumentException("AI instructions are required");
        if (StringUtils.isBlank(input)) throw new IllegalArgumentException("AI input must not be null");

        StrictSchemaValidator.validate(schema);

        String apiKey = ConfigurationLoader.requiredEnvironmentVariable(configuration.apiKeyEnvironmentVariable);
        String requestBody = buildRequest(instructions, input, schemaName, schema).toString();

        int attempts = Math.max(0, configuration.maxRetries) + 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI).timeout(Duration.ofSeconds(configuration.timeoutSeconds)).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) return parseStructuredResponse(response.body(), responseType);

                String requestId = response.headers().firstValue("x-request-id").orElse("unknown");

                if (!isRetryable(response.statusCode()) || attempt == attempts)  throw new IllegalStateException("OpenAI request failed with HTTP " + response.statusCode() + " (requestId=" + requestId + ")");

                sleepBeforeRetry(attempt, response.headers().firstValue("Retry-After").orElse(null));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OpenAI request was interrupted", exception);
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                if (attempt == attempts) throw new IllegalStateException("OpenAI request failed after " + attempts + " attempt(s)", exception);

                try {
                    sleepBeforeRetry(attempt, null);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("OpenAI retry wait was interrupted", interruptedException);
                }
            }
        }

        throw new IllegalStateException("OpenAI request did not complete");
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    private ObjectNode buildRequest(String instructions, String input, String schemaName, JsonNode schema) {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", configuration.model);
        request.put("store", false);
        request.put("instructions", instructions);
        request.put("input", input);

        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);

        return request;
    }

    private <T> T parseStructuredResponse(String responseBody, Class<T> responseType) {
        try {
            JsonNode response = mapper.readTree(responseBody);
            String outputText = findOutputText(response.path("output"));

            if (outputText == null || outputText.isBlank()) throw new IllegalStateException("OpenAI returned no structured output_text");

            return mapper.readValue(outputText, responseType);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI structured output could not be parsed as " + responseType.getSimpleName(), exception);
        }
    }

    private String findOutputText(JsonNode output) {
        if (!output.isArray()) return null;

        StringBuilder result = new StringBuilder();

        for (JsonNode item : output) {
            for (JsonNode content : item.path("content")) {
                if (!StringUtils.equals("output_text", content.path("type").asText())) continue;

                String text = content.path("text").asText("");

                if (StringUtils.isBlank(text)) continue;
                if (!result.isEmpty()) result.append('\n');

                result.append(text);
            }
        }

        return result.isEmpty() ? null : result.toString();
    }

    private boolean isRetryable(int statusCode) {
        return  statusCode == 408 ||
                statusCode == 409 ||
                statusCode == 429 ||
                statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt, String retryAfterHeader) throws InterruptedException {
        if (retryAfterHeader != null) {
            try {
                Thread.sleep(Math.min(Long.parseLong(retryAfterHeader.trim()) * 1000L, 30_000L));
                return;
            } catch (NumberFormatException ignored) {
            }
        }

        long base = Math.min(8_000L, 500L * (1L << Math.min(attempt - 1, 4)));
        Thread.sleep(base + ThreadLocalRandom.current().nextLong(0, 501));
    }
}

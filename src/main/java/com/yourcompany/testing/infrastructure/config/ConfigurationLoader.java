package com.yourcompany.testing.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigurationLoader {
    private ConfigurationLoader() {}

    public static ApplicationConfiguration load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        String externalConfiguration = firstNonBlank(System.getProperty("app.config"), System.getenv("APP_CONFIG_FILE"));

        try {
            if (externalConfiguration != null) {
                Path configurationPath = Path.of(externalConfiguration);
                if (!Files.isRegularFile(configurationPath))
                    throw new IllegalStateException("Application configuration file does not exist: " + configurationPath);

                try (InputStream inputStream = Files.newInputStream(configurationPath)) {
                    return mapper.readValue(inputStream, ApplicationConfiguration.class);
                }
            }

            try (InputStream inputStream = ConfigurationLoader.class.getClassLoader().getResourceAsStream("application.yml")) {
                if (inputStream == null) throw new IllegalStateException("Classpath application.yml was not found");

                return mapper.readValue(inputStream, ApplicationConfiguration.class);
            }

        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load application configuration", exception);
        }
    }

    public static String requiredEnvironmentVariable(String variableName) {
        if (StringUtils.isBlank(variableName)) throw new IllegalArgumentException("Environment variable name is required");

        String value = System.getenv(variableName);

        if (StringUtils.isBlank(value)) throw new IllegalStateException("Required environment variable is not configured: " + variableName);

        return value;
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.isNotBlank(first)) return first;
        if (StringUtils.isNotBlank(second)) return second;

        return null;
    }
}

package com.yourcompany.testing.infrastructure.privacy;

import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDataSanitizerTest {
    @Test
    void masksCredentialsAndTokensBeforeAiUse() {
        ApplicationConfiguration.TestDataPrivacyConfig configuration = new ApplicationConfiguration.TestDataPrivacyConfig();
        TestDataSanitizer sanitizer = new TestDataSanitizer(configuration);

        String sanitized = sanitizer.sanitize("username=real.user\npassword=secret123\nAuthorization: Bearer abc.def.ghi\ntoken=raw-token");

        assertTrue(sanitized.contains("${TEST_USERNAME}"));
        assertTrue(sanitized.contains("${TEST_PASSWORD}"));
        assertTrue(sanitized.contains("${TEST_TOKEN}"));
        assertFalse(sanitized.contains("real.user"));
        assertFalse(sanitized.contains("secret123"));
        assertFalse(sanitized.contains("abc.def.ghi"));
        assertFalse(sanitized.contains("raw-token"));
    }
}

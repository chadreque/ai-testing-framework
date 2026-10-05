package com.yourcompany.testing.infrastructure.privacy;

import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomSanitizerTableTest {
    @Test
    void preservesTableStructureAndNameButNeverCellValueAttribute() {
        ApplicationConfiguration.DomPrivacyConfig configuration = new ApplicationConfiguration.DomPrivacyConfig();
        DomSanitizer sanitizer = new DomSanitizer(configuration);

        String rawDom = """
                [
                  {"tag":"table","label":"","text":"","attributes":{"id":"clientTable","name":"client-table"}},
                  {"tag":"th","label":"","text":"Fullname","attributes":{"scope":"col"}},
                  {"tag":"td","label":"","text":"","attributes":{"name":"tableClintName","value":"Sensitive Name"}}
                ]
                """;

        String sanitized = sanitizer.sanitize(rawDom);

        assertTrue(sanitized.contains("clientTable"));
        assertTrue(sanitized.contains("tableClintName"));
        assertTrue(sanitized.contains("Fullname"));
        assertFalse(sanitized.contains("Sensitive Name"));
    }
}

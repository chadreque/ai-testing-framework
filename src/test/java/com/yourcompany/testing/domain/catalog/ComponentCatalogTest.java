package com.yourcompany.testing.domain.catalog;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentCatalogTest {
    @Test
    void newCatalogUsesCurrentFormatVersion() {
        ComponentCatalog catalog = new ComponentCatalog(
                "https://example.test/clients",
                "clients",
                Map.of());
    }
}

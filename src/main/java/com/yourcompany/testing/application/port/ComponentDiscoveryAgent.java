package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.catalog.ComponentDefinition;
import com.yourcompany.testing.domain.catalog.PageUrl;

import java.util.List;
import java.util.Optional;

public interface ComponentDiscoveryAgent {
    List<ComponentDefinition> discoverPage(PageUrl pageUrl, String pageName, String sanitizedDom);
    Optional<ComponentDefinition> discoverComponent(String logicalName, String sanitizedDom);
}

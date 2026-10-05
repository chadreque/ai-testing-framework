package com.yourcompany.testing.domain.catalog;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record ComponentCatalog(
        String pageUrl,
        String pageName,
        Map<String, ComponentDefinition> components
) {
    public ComponentCatalog {
        if (StringUtils.isBlank(pageUrl)) throw new IllegalArgumentException("Catalog page URL is required");

        pageName = StringUtils.isBlank(pageName) ? "page" : pageName;

        components = components == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(components));
    }

    public Optional<ComponentDefinition> find(String logicalName) {
        return Optional.ofNullable(components.get(logicalName));
    }

    public ComponentCatalog withComponent(ComponentDefinition component) {
        LinkedHashMap<String, ComponentDefinition> updatedComponents = new LinkedHashMap<>(components);

        updatedComponents.put(component.logicalName(), component);

        return new ComponentCatalog(pageUrl, pageName, updatedComponents);
    }
}

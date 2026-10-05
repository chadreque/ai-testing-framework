package com.yourcompany.testing.domain.catalog;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public record ComponentDefinition(
        String logicalName,
        String elementType,
        ComponentMetadata metadata,
        List<Selector> selectors
) {
    public ComponentDefinition {
        if (StringUtils.isBlank(logicalName)) throw new IllegalArgumentException("Component logical name is required");

        elementType = elementType == null ? "" : elementType;

        metadata = metadata == null ? new ComponentMetadata("", "", "", "", "") : metadata;

        selectors = selectors == null ? List.of() : List.copyOf(selectors);

        if (selectors.isEmpty()) throw new IllegalArgumentException("Component must contain at least one selector: " + logicalName);
    }
}

package com.yourcompany.testing.infrastructure.ai;

import com.yourcompany.testing.application.port.ComponentDiscoveryAgent;
import com.yourcompany.testing.domain.catalog.ComponentDefinition;
import com.yourcompany.testing.domain.catalog.ComponentMetadata;
import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.domain.catalog.Selector;
import com.yourcompany.testing.domain.catalog.SelectorStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class OpenAiComponentDiscoveryAgent implements ComponentDiscoveryAgent {
    private final OpenAiClient client;
    private final double minimumSelectorConfidence;

    public OpenAiComponentDiscoveryAgent(OpenAiClient client, double minimumSelectorConfidence) {
        this.client = client;
        this.minimumSelectorConfidence = Math.max(0.0, Math.min(1.0, minimumSelectorConfidence));
    }

    @Override
    public List<ComponentDefinition> discoverPage(PageUrl pageUrl, String pageName, String sanitizedDom) {
        String instructions = """
                You are an expert Selenium component catalog discovery engine.
                The supplied DOM snapshot has already been privacy-sanitized.
                Discover meaningful UI components that can be interacted with OR asserted in automated tests.
                This includes inputs, buttons, links, selects, dialogs, tables, table headers, repeated table cells/columns,
                lists, messages, labels, headings, semantic role elements, and custom elements when they have stable identifying attributes.
                Do not limit discovery to clickable or editable elements.
                
                Generate stable semantic logical names in the form page.component, for example login.username,
                clients.table, clients.fullNameHeader, clients.tableClientName, or vehicle.registrationNumber.
                Use the supplied page name as the logical prefix when reasonable.
                A logical name describes business/UI meaning, not the HTML implementation. Do not use names such as page.th, page.td, or page.div.
                
                For repeated cells such as <td name="tableClintName">...</td>, the stable name/id/role attribute may identify the repeated component.
                Never use dynamic customer data or cell text as a selector.
                For each component return up to five selectors ordered from most reliable to least reliable.
                Prefer data-testid, data-test, data-qa, stable id, stable name, aria-label, semantic CSS, then relative XPath.
                Never use absolute XPath, nth-child, generated/random CSS classes, random IDs, long DOM paths,
                image src values, input values, company names, logos, credentials, tokens, or secrets.
                
                SELECTOR EVIDENCE RULES:
                - Never invent a selector.
                - Never create, infer, rename or invent a logical component name.
                - Every logical component used by a generated step must exactly match one of the available component
                - Every selector must be directly supported by an element present in the supplied sanitized DOM.
                - Do not return a generic selector merely because it is syntactically valid.
                - Do not return selectors whose reason says unsupported, not present, not found, guessed, inferred, or missing.
                - Omit a component entirely if the DOM does not support at least one usable selector.
                - Confidence must be between 0 and 1.
                - If the requested test behaviour requires a component that is not available in the catalog, report that the step cannot be safely implemented from the current catalog.
                Return only data matching the supplied JSON schema.
                """;

        String input = "PAGE NAME: " + pageName + "\nSANITIZED DOM:\n" + sanitizedDom;

        AiPageDiscoveryResponse response = client.generateStructured(instructions, input, "page_component_catalog", OpenAiSchemaFactory.pageComponents(client.mapper()), AiPageDiscoveryResponse.class);

        if (response.components() == null) return List.of();

        return response.components().stream().map(this::toComponent).flatMap(Optional::stream).toList();
    }

    @Override
    public Optional<ComponentDefinition> discoverComponent(String logicalName, String sanitizedDom) {
        String instructions = """
                You are an expert Selenium locator recovery engine.
                Identify the exact sanitized DOM element represented by the requested logical component name.
                The component may be interactive or assertion-only, including a table, header, row/cell pattern, message, label, or custom element.
                Return up to five stable selectors ordered by reliability.
                Prefer data-testid, data-test, data-qa, stable id, stable name, aria-label, semantic CSS, then relative XPath.
                Never use absolute XPath, nth-child, generated/random CSS classes, random IDs, long DOM paths,
                image src values, input values, credentials, tokens, secrets, company names, logos, or dynamic business data.
                
                SELECTOR EVIDENCE RULES:
                - Never invent a selector.
                - Never create, infer, rename or invent a logical component name.
                - Every logical component used by a generated step must exactly match one of the available component
                - Every returned selector must be directly supported by the supplied sanitized DOM.
                - Do not return a generic fallback selector if the requested component cannot be identified.
                - If the component is not present or cannot be identified reliably, set found=false and return an empty components array.
                - When found=true, return exactly one component and its logicalName must exactly match the requested logical name.
                - Do not return selectors with blank values.
                - Do not return selectors whose reason says unsupported, not present, not found, guessed, inferred, or missing.
                - If the requested test behaviour requires a component that is not available in the catalog, report that the step cannot be safely implemented from the current catalog.
                Return only data matching the supplied JSON schema.
                """;
        String input = "REQUESTED LOGICAL COMPONENT: " + logicalName + "\nSANITIZED DOM:\n" + sanitizedDom;

        AiSingleComponentResponse response = client.generateStructured(instructions, input, "single_component_lookup", OpenAiSchemaFactory.singleComponentLookup(client.mapper()), AiSingleComponentResponse.class);

        if (response == null || !response.found() || response.components() == null || response.components().isEmpty())
            return Optional.empty();

        return toComponent(response.components().get(0)).map(component -> logicalName.equals(component.logicalName()) ? component : new ComponentDefinition(logicalName, component.elementType(), component.metadata(), component.selectors()));
    }

    private Optional<ComponentDefinition> toComponent(AiComponent candidate) {
        if (candidate == null || candidate.logicalName() == null || candidate.logicalName().isBlank())
            return Optional.empty();

        List<Selector> selectors = candidate.selectors() == null ? List.of() : candidate.selectors().stream().map(this::toSelector).flatMap(Optional::stream).limit(5).toList();

        if (selectors.isEmpty()) return Optional.empty();

        AiMetadata aiMetadata = candidate.metadata();
        ComponentMetadata metadata = aiMetadata == null ? new ComponentMetadata("", "", "", "", "") : new ComponentMetadata(safe(aiMetadata.expectedTag()), safe(aiMetadata.expectedRole()), safe(aiMetadata.expectedText()), safe(aiMetadata.expectedLabel()), safe(aiMetadata.expectedType()));

        return Optional.of(new ComponentDefinition(candidate.logicalName().trim(), safe(candidate.elementType()), metadata, selectors));
    }

    private Optional<Selector> toSelector(AiSelector candidate) {
        if (candidate == null || candidate.strategy() == null || candidate.strategy().isBlank() || candidate.value() == null || candidate.value().isBlank())
            return Optional.empty();

        double confidence = candidate.confidence() == null ? 0.0 : Math.max(0.0, Math.min(1.0, candidate.confidence()));

        if (confidence < minimumSelectorConfidence) return Optional.empty();

        String reason = safe(candidate.reason()).toLowerCase(Locale.ROOT);

        if (containsUnsupportedReason(reason)) return Optional.empty();

        try {
            SelectorStrategy strategy = SelectorStrategy.valueOf(candidate.strategy().trim().toUpperCase(Locale.ROOT));

            return Optional.of(new Selector(strategy, candidate.value().trim(), confidence, safe(candidate.reason())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean containsUnsupportedReason(String reason) {
        return reason.contains("not supported") || reason.contains("unsupported") || reason.contains("not present") || reason.contains("not found") || reason.contains("no element") || reason.contains("no matching") || reason.contains("cannot identify") || reason.contains("could not identify") || reason.contains("guess") || reason.contains("fabricat") || reason.contains("inferred without") || reason.contains("missing from");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record AiPageDiscoveryResponse(List<AiComponent> components) {
    }

    public record AiSingleComponentResponse(boolean found, String reason, List<AiComponent> components) {
    }

    public record AiComponent(String logicalName, String elementType, AiMetadata metadata, List<AiSelector> selectors) {
    }

    public record AiMetadata(String expectedTag, String expectedRole, String expectedText, String expectedLabel,
                             String expectedType) {
    }

    public record AiSelector(String strategy, String value, Double confidence, String reason) {
    }
}

package com.yourcompany.testing.application.catalog;

import com.yourcompany.testing.application.exception.CatalogException;
import com.yourcompany.testing.application.port.ComponentCatalogRepository;
import com.yourcompany.testing.application.port.ComponentDiscoveryAgent;
import com.yourcompany.testing.application.port.DomPrivacySanitizer;
import com.yourcompany.testing.application.port.PageDomCollector;
import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.ComponentDefinition;
import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.domain.catalog.Selector;
import com.yourcompany.testing.domain.catalog.SelectorStrategy;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class CatalogService {
    private final ComponentCatalogRepository repository;
    private final PageDomCollector pageDomCollector;
    private final DomPrivacySanitizer domSanitizer;
    private final ComponentDiscoveryAgent discoveryAgent;

    public CatalogService(ComponentCatalogRepository repository, PageDomCollector pageDomCollector, DomPrivacySanitizer domSanitizer, ComponentDiscoveryAgent discoveryAgent) {
        this.repository = repository;
        this.pageDomCollector = pageDomCollector;
        this.domSanitizer = domSanitizer;
        this.discoveryAgent = discoveryAgent;
    }

    public synchronized Optional<ComponentCatalog> findCatalog(PageUrl pageUrl) {
        return repository.find(pageUrl);
    }

    public synchronized Optional<ComponentDefinition> findComponent(PageUrl pageUrl, String logicalName) {
        if (logicalName == null || logicalName.isBlank()) return Optional.empty();
        return repository.find(pageUrl).flatMap(catalog -> catalog.find(logicalName));
    }

    public synchronized ComponentCatalog ensureCatalog(PageUrl pageUrl) {
        return repository.find(pageUrl).orElseGet(() -> createCatalog(pageUrl, () -> pageDomCollector.collect(pageUrl)));
    }

    public synchronized ComponentCatalog ensureCatalog(PageUrl pageUrl, Supplier<String> rawDomSupplier) {
        return repository.find(pageUrl).orElseGet(() -> createCatalog(pageUrl, rawDomSupplier));
    }

    public synchronized ComponentDefinition ensureComponent(PageUrl pageUrl, String logicalName, Supplier<String> rawDomSupplier) {
        ComponentCatalog catalog = ensureCatalog(pageUrl, rawDomSupplier);
        return catalog.find(logicalName).orElseGet(() -> refreshComponent(pageUrl, logicalName, rawDomSupplier));
    }

    public synchronized ComponentDefinition refreshComponent(PageUrl pageUrl, String logicalName, Supplier<String> rawDomSupplier) {
        if (logicalName == null || logicalName.isBlank())
            throw new CatalogException("Logical component name is required");

        ComponentCatalog currentCatalog = repository.find(pageUrl).orElseGet(() -> createCatalog(pageUrl, rawDomSupplier));

        String sanitizedDom = sanitizeDom(rawDomSupplier.get(), pageUrl);

        ComponentDefinition discoveredComponent = discoveryAgent.discoverComponent(logicalName, sanitizedDom).map(this::normalizeComponent).orElseThrow(() -> new CatalogException("AI could not identify a DOM-supported component for logical name '" + logicalName + "' on page " + pageUrl.normalizedIdentity()));

        if (!logicalName.equals(discoveredComponent.logicalName()))
            discoveredComponent = new ComponentDefinition(logicalName, discoveredComponent.elementType(), discoveredComponent.metadata(), discoveredComponent.selectors());

        ComponentCatalog updatedCatalog = currentCatalog.withComponent(discoveredComponent);

        repository.save(pageUrl, updatedCatalog);

        return discoveredComponent;
    }

    private ComponentCatalog createCatalog(PageUrl pageUrl, Supplier<String> rawDomSupplier) {
        try {
            String sanitizedDom = sanitizeDom(rawDomSupplier.get(), pageUrl);
            String pageName = pageName(pageUrl);

            List<ComponentDefinition> discoveredComponents = discoveryAgent.discoverPage(pageUrl, pageName, sanitizedDom);

            if (discoveredComponents == null || discoveredComponents.isEmpty())
                throw new CatalogException("AI did not discover any usable components for page: " + pageUrl.normalizedIdentity());

            Map<String, ComponentDefinition> components = new LinkedHashMap<>();

            for (ComponentDefinition component : discoveredComponents) {
                ComponentDefinition normalizedComponent = normalizeComponent(component);
                components.putIfAbsent(normalizedComponent.logicalName(), normalizedComponent);
            }

            ComponentCatalog catalog = new ComponentCatalog(pageUrl.normalizedIdentity(), pageName, components);

            repository.save(pageUrl, catalog);

            return catalog;
        } catch (CatalogException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CatalogException("Unable to generate component catalog for page: " + pageUrl.normalizedIdentity(), exception);
        }
    }

    private String sanitizeDom(String rawDom, PageUrl pageUrl) {
        if (StringUtils.isBlank(rawDom))
            throw new CatalogException("DOM extraction returned no content for page: " + pageUrl.normalizedIdentity());

        String sanitizedDom = domSanitizer.sanitize(rawDom);

        if (StringUtils.isBlank(sanitizedDom) || StringUtils.equals("[]", StringUtils.trim(sanitizedDom)))
            throw new CatalogException("No AI-eligible DOM content remained after privacy sanitization for page: " + pageUrl.normalizedIdentity());

        return sanitizedDom;
    }

    private ComponentDefinition normalizeComponent(ComponentDefinition component) {
        if (component == null) throw new CatalogException("AI returned a null component definition");
        if (!component.logicalName().matches("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*)+"))
            throw new CatalogException("AI returned an invalid logical component name: " + component.logicalName());

        List<Selector> sortedSelectors = new ArrayList<>(component.selectors());
        sortedSelectors.sort(Comparator.comparingInt(this::selectorPriority).thenComparing(Comparator.comparingDouble(Selector::confidence).reversed()));

        LinkedHashSet<String> uniqueSelectorKeys = new LinkedHashSet<>();
        List<Selector> uniqueSelectors = sortedSelectors.stream().filter(selector -> uniqueSelectorKeys.add(selector.strategy() + "\u0000" + selector.value())).limit(5).toList();

        if (uniqueSelectors.isEmpty())
            throw new CatalogException("Component contains no usable selectors: " + component.logicalName());

        return new ComponentDefinition(component.logicalName(), component.elementType(), component.metadata(), uniqueSelectors);
    }

    private int selectorPriority(Selector selector) {
        String selectorValue = selector.value().toLowerCase(Locale.ROOT);
        if (selector.strategy() == SelectorStrategy.CSS && (selectorValue.contains("data-testid") || selectorValue.contains("data-test=") || selectorValue.contains("data-qa=")))
            return 0;

        if (selector.strategy() == SelectorStrategy.CSS && (selectorValue.contains("aria-label") || selectorValue.contains("aria-labelledby")))
            return 3;

        return switch (selector.strategy()) {
            case ID -> 1;
            case NAME -> 2;
            case CSS -> 4;
            case LINK_TEXT -> 5;
            case XPATH -> 6;
            case CLASS_NAME -> 7;
            case TAG_NAME -> 8;
        };
    }

    public static String pageName(PageUrl pageUrl) {
        URI uri = URI.create(pageUrl.normalizedIdentity());

        String path = uri.getPath();

        if (StringUtils.isBlank(path) || StringUtils.equals("/", path)) return "home";

        String normalized = Normalizer.normalize(path, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");

        return StringUtils.isBlank(normalized) ? "page" : normalized;
    }
}

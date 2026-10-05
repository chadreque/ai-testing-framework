package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.application.catalog.CatalogService;
import com.yourcompany.testing.application.exception.CatalogException;
import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.ComponentDefinition;
import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.domain.catalog.Selector;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class SeleniumElementResolver {
    private final SeleniumBrowser browser;
    private final SeleniumDomExtractor domExtractor;
    private final CatalogService catalogService;
    private final SelectorConverter selectorConverter;
    private final boolean refreshOnSelectorFailure;
    private final ApplicationConfiguration.BrowserConfig browserConfiguration;

    public SeleniumElementResolver(
            SeleniumBrowser browser,
            SeleniumDomExtractor domExtractor,
            CatalogService catalogService,
            SelectorConverter selectorConverter,
            ApplicationConfiguration.BrowserConfig browserConfiguration,
            ApplicationConfiguration.CatalogConfig catalogConfiguration) {
        this.browser = browser;
        this.domExtractor = domExtractor;
        this.catalogService = catalogService;
        this.selectorConverter = selectorConverter;
        this.browserConfiguration = browserConfiguration;
        this.refreshOnSelectorFailure = catalogConfiguration.refreshComponentOnSelectorFailure;
    }

    public WebElement find(String logicalName) {
        validateLogicalName(logicalName);
        PageUrl pageUrl = currentPageUrl();
        Supplier<String> currentDom = () -> domExtractor.extract(browser.driver());
        ComponentCatalog catalog = catalogService.ensureCatalog(pageUrl, currentDom);
        ComponentDefinition component = catalog.find(logicalName)
                .orElseGet(() -> catalogService.ensureComponent(pageUrl, logicalName, currentDom));

        WebElement resolvedElement = trySelectors(component);
        if (resolvedElement != null) return resolvedElement;

        if (refreshOnSelectorFailure) {
            ComponentDefinition refreshedComponent = catalogService.refreshComponent(pageUrl, logicalName, currentDom);
            resolvedElement = trySelectors(refreshedComponent);
            if (resolvedElement != null) return resolvedElement;
        }

        throw new NoSuchElementException("Unable to resolve logical component '" + logicalName
                + "' on page " + pageUrl.normalizedIdentity());
    }

    public Optional<WebElement> findIfPresent(String logicalName) {
        validateLogicalName(logicalName);
        PageUrl pageUrl = currentPageUrl();
        Supplier<String> currentDom = () -> domExtractor.extract(browser.driver());
        ComponentCatalog catalog = catalogService.ensureCatalog(pageUrl, currentDom);
        return catalog.find(logicalName)
                .map(this::trySelectors)
                .filter(java.util.Objects::nonNull);
    }

    private PageUrl currentPageUrl() {
        String currentUrl = browser.currentUrl();
        if (isHttpUrl(currentUrl)) return new PageUrl(currentUrl);

        if (browserConfiguration.autoNavigateToDefaultPage) {
            String variableName = browserConfiguration.defaultPageUrlEnvironmentVariable;
            String configuredPageUrl = variableName == null || variableName.isBlank() ? null : System.getenv(variableName);
            if (isHttpUrl(configuredPageUrl)) {
                PageUrl pageUrl = new PageUrl(configuredPageUrl);
                browser.open(pageUrl);
                browser.waitUntilStable(Duration.ofSeconds(Math.max(1, browserConfiguration.domStabilityTimeoutSeconds)));
                return new PageUrl(browser.currentUrl());
            }
        }

        throw new IllegalStateException(
                "Browser is not currently on an HTTP/HTTPS application page. "
                        + "Navigate before resolving components"
                        + defaultPageHint()
                        + ". Current URL: " + currentUrl);
    }

    private String defaultPageHint() {
        if (!browserConfiguration.autoNavigateToDefaultPage) return "";
        String variableName = browserConfiguration.defaultPageUrlEnvironmentVariable;
        return variableName == null || variableName.isBlank()
                ? ""
                : " or configure environment variable " + variableName;
    }

    private boolean isHttpUrl(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private void validateLogicalName(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            throw new IllegalArgumentException("Logical component name is required");
        }
    }

    private WebElement trySelectors(ComponentDefinition component) {
        for (Selector selector : component.selectors()) {
            try {
                List<WebElement> matches = browser.driver().findElements(selectorConverter.toBy(selector));
                if (matches.isEmpty()) continue;
                for (WebElement match : matches) {
                    try {
                        if (match.isDisplayed()) return match;
                    } catch (StaleElementReferenceException ignored) {
                        // Try another match or selector.
                    }
                }
                try {
                    return matches.get(0);
                } catch (StaleElementReferenceException ignored) {
                    // Continue with the next selector.
                }
            } catch (RuntimeException selectorFailure) {
                if (selectorFailure instanceof CatalogException) throw selectorFailure;
            }
        }
        return null;
    }
}

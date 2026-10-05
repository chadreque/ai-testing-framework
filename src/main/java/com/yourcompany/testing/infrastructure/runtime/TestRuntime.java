package com.yourcompany.testing.infrastructure.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.testing.application.catalog.CatalogService;
import com.yourcompany.testing.infrastructure.ai.OpenAiClient;
import com.yourcompany.testing.infrastructure.ai.OpenAiComponentDiscoveryAgent;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.infrastructure.config.ConfigurationLoader;
import com.yourcompany.testing.infrastructure.persistence.JsonComponentCatalogRepository;
import com.yourcompany.testing.infrastructure.privacy.DomSanitizer;
import com.yourcompany.testing.infrastructure.selenium.PageActions;
import com.yourcompany.testing.infrastructure.selenium.SelectorConverter;
import com.yourcompany.testing.infrastructure.selenium.SeleniumBrowser;
import com.yourcompany.testing.infrastructure.selenium.SeleniumDomExtractor;
import com.yourcompany.testing.infrastructure.selenium.SeleniumElementResolver;
import com.yourcompany.testing.infrastructure.selenium.SeleniumPageDomCollector;

import java.nio.file.Path;

public final class TestRuntime implements AutoCloseable {
    private static final ThreadLocal<TestRuntime> CURRENT = new ThreadLocal<>();

    private final SeleniumBrowser browser;
    private final PageActions pageActions;

    private TestRuntime(Path projectRoot) {
        ApplicationConfiguration configuration = ConfigurationLoader.load();
        OpenAiClient aiClient = new OpenAiClient(configuration.ai);
        ObjectMapper mapper = new ObjectMapper();
        SeleniumDomExtractor domExtractor = new SeleniumDomExtractor(mapper, configuration.browser);

        CatalogService catalogService = new CatalogService(
                new JsonComponentCatalogRepository(projectRoot, configuration.catalog.directory),
                new SeleniumPageDomCollector(configuration.browser, domExtractor),
                new DomSanitizer(configuration.ai.privacy.dom),
                new OpenAiComponentDiscoveryAgent(aiClient, configuration.catalog.minimumSelectorConfidence)
        );

        this.browser = new SeleniumBrowser(configuration.browser);

        SeleniumElementResolver elementResolver = new SeleniumElementResolver(
                browser,
                domExtractor,
                catalogService,
                new SelectorConverter(),
                configuration.browser,
                configuration.catalog);

        this.pageActions = new PageActions(browser, elementResolver);
    }

    public static void start() {
        if (CURRENT.get() != null) return;

        System.out.println("DIR: " + Path.of(System.getProperty("user.dir")));

        TestRuntime runtime = new TestRuntime(Path.of(System.getProperty("user.dir")));

        CURRENT.set(runtime);
    }

    public static TestRuntime current() {
        TestRuntime runtime = CURRENT.get();

        if (runtime == null) {
            start();
            runtime = CURRENT.get();
        }

        return runtime;
    }

    public PageActions pageActions() {
        return pageActions;
    }

    @Override
    public void close() {
        try {
            browser.close();
        } finally {
            CURRENT.remove();
        }
    }

    public static void closeCurrent() {
        TestRuntime runtime = CURRENT.get();
        if (runtime != null) runtime.close();
    }
}

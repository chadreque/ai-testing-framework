package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.application.port.PageDomCollector;
import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;

public final class SeleniumPageDomCollector implements PageDomCollector {
    private final ApplicationConfiguration.BrowserConfig browserConfiguration;
    private final SeleniumDomExtractor domExtractor;

    public SeleniumPageDomCollector(ApplicationConfiguration.BrowserConfig browserConfiguration, SeleniumDomExtractor domExtractor) {
        this.browserConfiguration = browserConfiguration;
        this.domExtractor = domExtractor;
    }

    @Override
    public String collect(PageUrl pageUrl) {
        try (SeleniumBrowser browser = new SeleniumBrowser(browserConfiguration)) {
            browser.open(pageUrl);
            return domExtractor.extract(browser.driver());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to extract DOM for page: " + pageUrl.normalizedIdentity(), exception);
        }
    }
}

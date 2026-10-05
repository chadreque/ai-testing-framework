package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.infrastructure.testing.DataResolver;
import org.openqa.selenium.WebElement;

import java.time.Duration;

public final class PageActions {
    private final SeleniumBrowser browser;
    private final SeleniumElementResolver elementResolver;

    public PageActions(SeleniumBrowser browser, SeleniumElementResolver elementResolver) {
        this.browser = browser;
        this.elementResolver = elementResolver;
    }

    public void open(String pageUrl) {
        browser.open(new PageUrl(DataResolver.resolve(pageUrl)));
    }

    public void type(String logicalComponentName, String value) {
        WebElement element = elementResolver.find(logicalComponentName);
        element.clear();
        element.sendKeys(DataResolver.resolve(value));
    }

    public void click(String logicalComponentName) {
        elementResolver.find(logicalComponentName).click();
    }

    public String text(String logicalComponentName) {
        return elementResolver.find(logicalComponentName).getText();
    }

    public boolean isDisplayed(String logicalComponentName) {
        return elementResolver
                .findIfPresent(logicalComponentName)
                .map(WebElement::isDisplayed)
                .orElse(false);
    }

    public String currentUrl() {
        return browser.currentUrl();
    }

    public void waitForUrl(String expectedUrl) {
        browser.waitForUrl(DataResolver.resolve(expectedUrl));
    }

    public void waitUntilStable(Duration duration) {
        browser.waitUntilStable(duration);
    }
}

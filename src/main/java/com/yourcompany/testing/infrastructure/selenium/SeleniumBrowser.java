package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Locale;

public final class SeleniumBrowser implements AutoCloseable {
    private final ApplicationConfiguration.BrowserConfig configuration;
    private WebDriver driver;

    public SeleniumBrowser(ApplicationConfiguration.BrowserConfig configuration) {
        this.configuration = configuration;
    }

    public synchronized WebDriver driver() {
        if (driver == null) driver = createDriver();
        return driver;
    }

    public void open(PageUrl pageUrl) {
        driver().navigate().to(pageUrl.value());
    }

    public String currentUrl() {
        return driver().getCurrentUrl();
    }

    public void waitForUrl(String expectedUrl) {
        if (expectedUrl == null || expectedUrl.isBlank()) {
            throw new IllegalArgumentException("Expected URL is required");
        }

        boolean matched = new WebDriverWait(driver(), Duration.ofSeconds(configuration.waitTimeoutSeconds))
                .until(webDriver -> expectedUrl.equals(webDriver.getCurrentUrl()));
        if (!matched) throw new IllegalStateException("Browser did not reach the expected URL");
    }

    public void waitUntilStable(Duration timeout) {
        WebDriver webDriver = driver();
        if (!(webDriver instanceof JavascriptExecutor javascriptExecutor)) return;
        new WebDriverWait(webDriver, timeout).until(currentDriver ->
                "complete".equals(String.valueOf(javascriptExecutor.executeScript("return document.readyState"))));
    }

    private WebDriver createDriver() {
        String browserType = configuration.type == null ? "chrome" : configuration.type.trim().toLowerCase(Locale.ROOT);
        WebDriver webDriver = switch (browserType) {
            case "chrome" -> new ChromeDriver(chromeOptions());
            case "firefox" -> new FirefoxDriver(firefoxOptions());
            case "edge" -> new EdgeDriver(edgeOptions());
            default -> throw new IllegalArgumentException("Unsupported browser type: " + browserType);
        };
        webDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(configuration.pageLoadTimeoutSeconds));
        return webDriver;
    }

    private ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();
        if (configuration.headless) options.addArguments("--headless=new");
        options.addArguments("--disable-dev-shm-usage");
        return options;
    }

    private FirefoxOptions firefoxOptions() {
        FirefoxOptions options = new FirefoxOptions();
        if (configuration.headless) options.addArguments("-headless");
        return options;
    }

    private EdgeOptions edgeOptions() {
        EdgeOptions options = new EdgeOptions();
        if (configuration.headless) options.addArguments("--headless=new");
        return options;
    }

    @Override
    public synchronized void close() {
        if (driver == null) return;
        try {
            driver.quit();
        } finally {
            driver = null;
        }
    }
}

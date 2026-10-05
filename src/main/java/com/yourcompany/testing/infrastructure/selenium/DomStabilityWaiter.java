package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public final class DomStabilityWaiter {
    private static final String INSTALL_OBSERVER_SCRIPT = """
            const quietPeriod = arguments[0];
            const minimumObservation = arguments[1];
            const now = Date.now();

            if (window.__aiCatalogDomObserver) {
                window.__aiCatalogDomObserver.disconnect();
            }

            window.__aiCatalogDomStartedAt = now;
            window.__aiCatalogDomLastMutationAt = now;

            window.__aiCatalogDomObserver = new MutationObserver(() => {
                window.__aiCatalogDomLastMutationAt = Date.now();
            });

            window.__aiCatalogDomObserver.observe(document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                characterData: false
            });

            return true;
            """;

    private static final String IS_STABLE_SCRIPT = """
            const quietPeriod = arguments[0];
            const minimumObservation = arguments[1];
            const now = Date.now();

            const observedLongEnough =
                now - (window.__aiCatalogDomStartedAt || now) >= minimumObservation;
            const mutationQuiet =
                now - (window.__aiCatalogDomLastMutationAt || now) >= quietPeriod;

            let angularStable = true;
            try {
                if (typeof window.getAllAngularTestabilities === 'function') {
                    const testabilities = window.getAllAngularTestabilities();
                    if (testabilities && testabilities.length > 0) {
                        angularStable = testabilities.every(testability => testability.isStable());
                    }
                }
            } catch (ignored) {
                angularStable = true;
            }

            return observedLongEnough && mutationQuiet && angularStable;
            """;

    private final Duration timeout;
    private final long quietPeriodMillis;
    private final long minimumObservationMillis;

    public DomStabilityWaiter(ApplicationConfiguration.BrowserConfig configuration) {
        this.timeout = Duration.ofSeconds(Math.max(1, configuration.domStabilityTimeoutSeconds));
        this.quietPeriodMillis = Math.max(100, configuration.domQuietPeriodMillis);
        this.minimumObservationMillis = Math.max(quietPeriodMillis, configuration.domMinimumObservationMillis);
    }

    public void waitUntilStable(WebDriver webDriver) {
        if (!(webDriver instanceof JavascriptExecutor javascriptExecutor)) {
            return;
        }

        WebDriverWait wait = new WebDriverWait(webDriver, timeout);
        wait.until(driver -> "complete".equals(String.valueOf(
                javascriptExecutor.executeScript("return document.readyState"))));

        javascriptExecutor.executeScript(
                INSTALL_OBSERVER_SCRIPT,
                quietPeriodMillis,
                minimumObservationMillis);

        wait.until(driver -> Boolean.TRUE.equals(javascriptExecutor.executeScript(
                IS_STABLE_SCRIPT,
                quietPeriodMillis,
                minimumObservationMillis)));
    }
}

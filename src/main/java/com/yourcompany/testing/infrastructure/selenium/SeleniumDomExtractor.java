package com.yourcompany.testing.infrastructure.selenium;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

public final class SeleniumDomExtractor {
    private static final String DOM_SCRIPT = """
            const maxElements = Math.max(1, Number(arguments[0] || 4000));
            const permanentlyBlockedAttributes = new Set(['value', 'src', 'srcset']);
            const ignoredTags = new Set(['script', 'style', 'meta', 'link', 'head', 'title', 'noscript']);
            const textTags = new Set([
                'button', 'a', 'label', 'legend', 'summary', 'option', 'caption',
                'h1', 'h2', 'h3', 'h4', 'h5', 'h6'
            ]);
            
            const elements = Array.from(document.querySelectorAll('*'))
                .filter(element => !ignoredTags.has((element.tagName || '').toLowerCase()))
                .slice(0, maxElements);
            
            return elements.map((element) => {
              const tag = (element.tagName || '').toLowerCase();
              const attributes = {};
              for (const attribute of Array.from(element.attributes || [])) {
                const attributeName = attribute.name.toLowerCase();
                if (!permanentlyBlockedAttributes.has(attributeName)) {
                  attributes[attribute.name] = attribute.value;
                }
              }
            
              let label = '';
              if (element.labels && element.labels.length > 0) {
                label = Array.from(element.labels)
                    .map(item => item.innerText || item.textContent || '')
                    .join(' ')
                    .trim();
              }
              if (!label) label = element.getAttribute('aria-label') || '';
              if (!label && element.id) {
                const explicitLabel = document.querySelector('label[for="' + CSS.escape(element.id) + '"]');
                if (explicitLabel) label = (explicitLabel.innerText || explicitLabel.textContent || '').trim();
              }
            
              let text = '';
              const isColumnHeader = tag === 'th' && (element.getAttribute('scope') || '').toLowerCase() !== 'row';
              if (textTags.has(tag) || isColumnHeader) {
                text = (element.innerText || element.textContent || '')
                    .trim()
                    .replace(/\\s+/g, ' ')
                    .slice(0, 240);
              }
            
              return {
                tag: tag,
                label: label.slice(0, 240),
                text: text,
                attributes: attributes
              };
            });
            """;

    private final ObjectMapper mapper;
    private final DomStabilityWaiter domStabilityWaiter;
    private final int maximumElements;

    public SeleniumDomExtractor(ObjectMapper mapper, ApplicationConfiguration.BrowserConfig configuration) {
        this.mapper = mapper;
        this.domStabilityWaiter = new DomStabilityWaiter(configuration);
        this.maximumElements = Math.max(1, configuration.domMaximumElements);
    }

    public String extract(WebDriver webDriver) {
        if (!(webDriver instanceof JavascriptExecutor javascriptExecutor))
            throw new IllegalArgumentException("WebDriver does not support JavaScript DOM extraction");

        domStabilityWaiter.waitUntilStable(webDriver);

        Object snapshot = javascriptExecutor.executeScript(DOM_SCRIPT, maximumElements);

        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize extracted DOM snapshot", exception);
        }
    }
}

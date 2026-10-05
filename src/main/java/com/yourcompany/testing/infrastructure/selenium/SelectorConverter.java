package com.yourcompany.testing.infrastructure.selenium;

import com.yourcompany.testing.domain.catalog.Selector;
import org.openqa.selenium.By;

public final class SelectorConverter {
    public By toBy(Selector selector) {
        return switch (selector.strategy()) {
            case ID -> By.id(selector.value());
            case NAME -> By.name(selector.value());
            case CSS -> By.cssSelector(selector.value());
            case XPATH -> By.xpath(selector.value());
            case CLASS_NAME -> By.className(selector.value());
            case TAG_NAME -> By.tagName(selector.value());
            case LINK_TEXT -> By.linkText(selector.value());
        };
    }
}

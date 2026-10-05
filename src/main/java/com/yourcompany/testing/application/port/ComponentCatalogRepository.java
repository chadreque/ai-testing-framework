package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.PageUrl;

import java.util.Optional;

public interface ComponentCatalogRepository {
    Optional<ComponentCatalog> find(PageUrl pageUrl);
    void save(PageUrl pageUrl, ComponentCatalog catalog);
}

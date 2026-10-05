# Consolidated fixes applied

This version applies the fixes discussed after the original merge:

- Java 17 build target retained.
- Selenium updated to 4.50.0 for CDP 154 support.
- Cucumber `${TEST_*}` placeholders are kept out of annotation expressions by existing validation/repair rules.
- `TestRuntime.start()` remains lifecycle-owned (`void`) to avoid ignored `AutoCloseable` warnings.
- `isDisplayed()` ensures the page catalog exists but does not trigger single-component AI self-healing when a logical component is absent; absence therefore cleanly becomes `false`.
- Single-component AI discovery can explicitly return `found=false` instead of fabricating an empty selector.
- AI output is deserialized into infrastructure DTOs and validated before strict domain `Selector`/`ComponentDefinition` objects are constructed.
- Blank, unsupported, suspicious, or low-confidence selector candidates are rejected.
- Full-page discovery now includes assertion-relevant components such as tables, headers and repeated table cells, not only interactive controls.
- DOM extraction uses `querySelectorAll('*')`, so standard/custom HTML elements are not accidentally excluded by a hard-coded tag list.
- Sensitive `value`, `src`, and `srcset` attributes remain blocked before AI use; dynamic `td` text is not collected by default.
- DOM extraction waits for document readiness, Angular testability (when available), and a configurable DOM mutation quiet period.
- Runtime can optionally navigate from `TEST_PAGE_URL` when a scenario attempts component resolution before any HTTP/HTTPS page is open.
- Error messages for invalid runtime page state are clearer.
- Catalog format version is now 2. Older catalogs are treated as stale and automatically regenerated on next use.
- Selector confidence threshold is configurable (`catalog.minimum-selector-confidence`).
- Browser DOM stability settings and maximum extracted element count are configurable.
- Feature/step generation prompts were tightened so each UI scenario navigates independently and data-environment preconditions are not invented as arbitrary visibility checks.

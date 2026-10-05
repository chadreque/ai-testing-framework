# AI Testing Framework — Merge Report

## Final architecture

The two source projects were merged into one Java 17 Maven application under the base package `com.yourcompany.testing`.

Java compatibility target: **Java 17** (`maven.compiler.release=17`). The previous Java 21-only `List.getFirst()` usage was replaced with Java 17-compatible access, and the runtime lifecycle API was adjusted so `TestRuntime.start()` no longer returns an ignored `AutoCloseable`.

```text
com.yourcompany.testing
├── domain
│   ├── catalog
│   ├── source
│   └── testcase
├── application
│   ├── catalog
│   ├── generation
│   ├── port
│   └── source
├── infrastructure
│   ├── ai
│   ├── bootstrap
│   ├── config
│   ├── jira
│   ├── persistence
│   ├── privacy
│   ├── runtime
│   ├── selenium
│   ├── source
│   └── testing
└── interfaces
    └── cli
```

The domain contains no Selenium, OpenAI, Jira, Apache POI, PDFBox, Cucumber, or CLI dependencies. Application services depend on ports. Infrastructure implements those ports.

## Main integration decisions

- One `CatalogService` owns catalog lookup, generation, persistence, missing-component discovery and component refresh. Both generation-time and Cucumber runtime flows reuse it.
- Catalog identity is deterministic from normalized page URL. JSON catalog files use a readable page slug plus a stable URL hash; timestamps are not used.
- `SeleniumElementResolver` receives only logical component names. Selenium selectors exist only in the catalog/domain selector model and Selenium infrastructure.
- DOM extraction never captures `value`, `src`, or `srcset`. `DomSanitizer` applies configurable allow/exclude/redaction rules before any DOM reaches AI.
- Human test source is sanitized before AI interpretation. An explicitly labeled page URL is extracted locally and replaced with `[PAGE_URL_REDACTED]` before AI processing.
- Feature generation sees `${TEST_PAGE_URL}` instead of the real page URL. Runtime resolves `${TEST_*}` values from environment variables.
- Feature files are deterministic per input source and contain stable `# @generated-id:` comments for generated scenarios. This makes repeated generation idempotent even if AI wording changes.
- Existing feature scenarios are preserved. Only new generated identities/names/step fingerprints are appended.
- Existing Java step annotations are scanned before generation. Generated Java is merged by Cucumber expression and validated before it is written.
- Generated Java is rejected if it contains direct WebDriver/WebElement/By usage, selector-looking strings, `Thread.sleep`, hard-coded secrets, or meaningless parameter names.
- PDF, `.doc`, `.docx`, TXT and Jira all produce the same `TestSourceContent` representation.
- Jira uses the Java `HttpClient` REST integration; no Atlassian client dependency is required.
- No generated Cucumber source is written under `target/`. Output-path validation rejects a configured `target/` location.
- No demo feature, sample page, fake Jira implementation, sample generated step class, or sample catalog is included.

## Existing class disposition

### Project A — AI Selenium DOM Catalog Generator

| Existing class/concept | Final disposition |
|---|---|
| `ComponentCatalog` | Retained and moved to `domain.catalog`; changed to deterministic per-page catalog model. |
| `ComponentDefinition` | Retained and moved to `domain.catalog`. |
| `ComponentMetadata` | Retained and moved to `domain.catalog`. |
| `AiSelector` | Renamed/reworked as `Selector`. |
| `SelectorType` | Renamed as `SelectorStrategy`. |
| `ComponentCatalogLoader` + `ComponentCatalogGenerator` | Split/merged into `ComponentCatalogRepository`, `JsonComponentCatalogRepository`, and `CatalogService`. |
| `DomExtractor` | Replaced by `SeleniumDomExtractor`; output is real JSON and sensitive value/src attributes are never collected. |
| `DomSanitizer` | Retained in purpose and replaced with configurable JSON-aware `DomSanitizer`. |
| `AiComponentDiscoveryAgent` | Retained as application port `ComponentDiscoveryAgent`. |
| `OpenAiComponentDiscoveryAgent` | Retained and adapted to structured Responses API output and sanitized DOM only. |
| `AiSelectorAgent` + `OpenAiSelectorAgent` | Merged into component discovery/recovery in `OpenAiComponentDiscoveryAgent`. |
| `AiPromptDscoverBuilder` + `AiPromptLocatorBuilder` | Merged into the focused OpenAI infrastructure agents; separate prompt-builder classes removed. |
| `SelectorConverter` | Retained and moved to `infrastructure.selenium`. |
| `SelectorValidator` + `SemanticValidator` | Their responsibilities are simplified into catalog normalization, ordered fallback selectors, Selenium resolution, and AI constraints. Standalone classes removed. |
| `SelfHealingSelectorResolver` | Replaced by `SeleniumElementResolver` plus `CatalogService.refreshComponent`. |
| `HealingEvent`, `HealingReporter`, `HealingResult` | Removed; no separate reporting subsystem was required by the merged specification. |
| `AiElement` + `AiElementFactory` | Replaced by `SeleniumElementResolver` and `PageActions`. |
| `BrowserManager` + `SeleniumBrowserManager` | Merged/renamed as `SeleniumBrowser`. |
| `AutomationConfig` | Replaced by `ApplicationConfiguration`, `ConfigurationLoader`, and `application.yml`. |
| `DataResolver` | Merged with the equivalent Project B concept as one `infrastructure.testing.DataResolver`. |
| `TestContext` | Replaced by per-thread `TestRuntime` used by Cucumber hooks and generated steps. |
| `App` | Removed; CLI entry point is `TestGeneratorCli`. |
| sample login feature/steps/catalog | Removed. |

### Project B — AI Test Case Generator

| Existing class/concept | Final disposition |
|---|---|
| `TestGenerator` | Renamed/reworked as `interfaces.cli.TestGeneratorCli`; only parses the command and delegates. |
| `TestGenerationPipeline` | Replaced by focused `TestGenerationApplicationService`. Old commented duplicate pipeline logic is removed. |
| `Agents` | Replaced by specific `OpenAiTestCaseInterpreter`, `OpenAiFeatureGenerator`, and `OpenAiStepDefinitionGenerator`. |
| `RepairAgent` | Standalone class removed; step repair is handled by bounded regeneration with validation feedback in the application service. |
| `OpenAiClient` | Retained and adapted as provider-specific infrastructure. |
| `SchemaFactory` | Renamed as `OpenAiSchemaFactory`. |
| `StrictSchemaValidator` | Retained. |
| `TestCase`, `TestStep`, `TestDataItem`, `FeatureModel`, `ScenarioModel`, `GherkinStep`, `StepCatalog`, step models | Retained/adapted under `domain.testcase`; multi-case `TestSpecification` was added. |
| `PdfExtractor` | Renamed/reworked as `PdfTestSourceReader`. |
| `DocxExtractor` | Replaced by `WordTestSourceReader`, which supports both `.docx` and `.doc`. |
| `TextExtractor` | Renamed/reworked as `TextTestSourceReader`. |
| `ExcelExtractor` | Removed because Excel is outside the requested input contract. |
| `RawTestCaseExtractor` + `TestCaseInputLoader` | Replaced by the common `TestSourceReader` port and `CompositeTestSourceReader`. |
| `JiraClient`, `JiraAdfParser` | Retained/adapted in `infrastructure.jira`. |
| `JiraConfig`, `JiraIssue` | Folded into central application configuration and the Jira client boundary. |
| `JiraTestCaseInput`, `JiraTestCaseInputLoader` | Replaced by `TestSource.jira(...)` and `JiraTestSourceReader`. |
| `SensitiveDataSanitizer` | Replaced by configurable `TestDataSanitizer`. |
| `ExistingStepScanner` | Retained/adapted under `application.generation`. |
| `GherkinValidator` + `SourceValidator` | Replaced by `FeatureValidator` and `GeneratedSourceValidator`. |
| `Writers` | Split into `FeatureFileService` and `StepSourceService`. |
| `FrameworkContract` | Removed as a separate class; the contract is enforced by generated-source validation and the `PageActions`/`TestRuntime` API. |
| `GenerationBundle`, old `StepModel` | Removed after responsibilities were split into explicit domain models/services. |
| duplicate `DataResolver` | Consolidated into one implementation. |

## Generation flow

```text
TestGeneratorCli
  -> TestGenerationApplicationService
  -> TestSourceReader (PDF/DOC/DOCX/TXT/Jira)
  -> TestDataSanitizer
  -> OpenAiTestCaseInterpreter
  -> TestSpecification
  -> optional CatalogService.ensureCatalog(pageUrl)
  -> OpenAiFeatureGenerator
  -> FeatureFileService (deterministic create/merge)
  -> ExistingStepScanner
  -> OpenAiStepDefinitionGenerator
  -> GeneratedSourceValidator
  -> StepSourceService (create/merge)
```

## Runtime flow

```text
Cucumber scenario
  -> generated step definition
  -> TestRuntime
  -> PageActions
  -> SeleniumElementResolver(logicalName)
  -> CatalogService.ensureCatalog(currentPageUrl)
  -> known selectors, in priority order
  -> optional component refresh when all selectors fail
  -> Selenium WebElement
```

The step definition is unaware of catalog creation or self-healing.

## Configuration and secrets

Default configuration is `src/main/resources/application.yml`. A different YAML file can be selected with `-Dapp.config=/path/application.yml` or `APP_CONFIG_FILE`.

Required OpenAI environment variable:

```bash
export OPENAI_API_KEY='...'
```

When a test source contains an explicit page URL, that URL is used locally for generation and is represented to generated tests as `${TEST_PAGE_URL}`:

```bash
export TEST_PAGE_URL='https://application.example/path'
```

Jira source generation requires:

```bash
export JIRA_BASE_URL='https://your-domain.atlassian.net'
export JIRA_EMAIL='user@example.com'
export JIRA_API_TOKEN='...'
```

Actual sensitive values such as `${TEST_USERNAME}` and `${TEST_PASSWORD}` are resolved at execution time from environment variables.

## Commands

```bash
mvn clean package
java -jar target/ai-testing-framework-2.0.0.jar file /path/to/tests.pdf
java -jar target/ai-testing-framework-2.0.0.jar file /path/to/tests.docx
java -jar target/ai-testing-framework-2.0.0.jar file /path/to/tests.doc
java -jar target/ai-testing-framework-2.0.0.jar file /path/to/tests.txt
java -jar target/ai-testing-framework-2.0.0.jar jira PROJECT-123
mvn test
```

## Validation performed in the merge environment

- `domain + application` compiled with the `javac --release 17`.
- All main Java sources compiled against local API stubs for Jackson, Selenium, PDFBox and Apache POI, catching Java-level integration/typing errors without changing production sources.
- Test Java sources compiled against local Cucumber/JUnit API stubs.
- A generated step source was produced through `StepSourceService` and compiled with Java 17 compatibility; it contained only logical component usage, no selectors.
- Feature idempotency was executed locally, including a repeat generation where the second AI-style feature/scenario wording was intentionally different; the deterministic generated ID prevented duplication.
- Sensitive test-data replacement was executed locally and verified to remove password/token/Bearer values.
- `pom.xml` is well-formed XML and `application.yml` parsed successfully.

Maven itself could not be executed in the merge environment because the `mvn` executable and a Maven dependency cache are not installed there. A real `mvn clean package` should therefore be the first verification command in the target development environment.

## Consolidated runtime/catalog fixes — 2026-10-03

See `FIXES_APPLIED.md` for the complete list. This revision keeps Java 17, updates Selenium to 4.50.0, adds CDP 154 support through that Selenium release, makes DOM extraction generic (`*`) and privacy-aware, waits for dynamic Angular/DOM rendering, includes assertion-relevant table structures, prevents invalid AI selectors from reaching the domain model, supports `found=false` for missing components, prevents `isDisplayed()` from invoking single-component self-healing, improves runtime page URL fallback via `TEST_PAGE_URL`, and versions catalogs so older snapshots are regenerated.

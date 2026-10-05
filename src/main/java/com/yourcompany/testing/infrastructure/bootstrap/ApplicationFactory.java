package com.yourcompany.testing.infrastructure.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.testing.application.catalog.CatalogService;
import com.yourcompany.testing.application.generation.ExistingStepScanner;
import com.yourcompany.testing.application.generation.FeatureFileService;
import com.yourcompany.testing.application.generation.FeatureValidator;
import com.yourcompany.testing.application.generation.GeneratedSourceValidator;
import com.yourcompany.testing.application.generation.PageUrlExtractor;
import com.yourcompany.testing.application.generation.StepSourceService;
import com.yourcompany.testing.application.generation.TestGenerationApplicationService;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.application.source.CompositeTestSourceReader;
import com.yourcompany.testing.infrastructure.ai.OpenAiClient;
import com.yourcompany.testing.infrastructure.ai.OpenAiComponentDiscoveryAgent;
import com.yourcompany.testing.infrastructure.ai.OpenAiFeatureGenerator;
import com.yourcompany.testing.infrastructure.ai.OpenAiStepDefinitionGenerator;
import com.yourcompany.testing.infrastructure.ai.OpenAiTestCaseInterpreter;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;
import com.yourcompany.testing.infrastructure.config.ConfigurationLoader;
import com.yourcompany.testing.infrastructure.jira.JiraClient;
import com.yourcompany.testing.infrastructure.persistence.JsonComponentCatalogRepository;
import com.yourcompany.testing.infrastructure.privacy.DomSanitizer;
import com.yourcompany.testing.infrastructure.privacy.TestDataSanitizer;
import com.yourcompany.testing.infrastructure.selenium.SeleniumDomExtractor;
import com.yourcompany.testing.infrastructure.selenium.SeleniumPageDomCollector;
import com.yourcompany.testing.infrastructure.source.JiraTestSourceReader;
import com.yourcompany.testing.infrastructure.source.PdfTestSourceReader;
import com.yourcompany.testing.infrastructure.source.TextTestSourceReader;
import com.yourcompany.testing.infrastructure.source.WordTestSourceReader;

import java.nio.file.Path;
import java.util.List;

public final class ApplicationFactory {
    private ApplicationFactory() {
    }

    public static TestGenerationApplicationService createGenerationService(Path projectRoot) {
        ApplicationConfiguration configuration = ConfigurationLoader.load();
        OpenAiClient aiClient = new OpenAiClient(configuration.ai);
        CatalogService catalogService = createCatalogService(projectRoot, configuration, aiClient);

        TestSourceReader sourceReader = new CompositeTestSourceReader(List.of(
                new PdfTestSourceReader(),
                new WordTestSourceReader(),
                new TextTestSourceReader(),
                new JiraTestSourceReader(new JiraClient(configuration.jira)))
        );

        return new TestGenerationApplicationService(
                sourceReader,
                new TestDataSanitizer(configuration.ai.privacy.testData),
                new OpenAiTestCaseInterpreter(aiClient),
                new OpenAiFeatureGenerator(aiClient),
                catalogService,
                new FeatureValidator(),
                new FeatureFileService(),
                new ExistingStepScanner(),
                new OpenAiStepDefinitionGenerator(aiClient),
                new StepSourceService(new GeneratedSourceValidator(), configuration.application.basePackage),
                new PageUrlExtractor(),
                configuration);
    }

    public static CatalogService createCatalogService(Path projectRoot, ApplicationConfiguration configuration, OpenAiClient aiClient) {
        ObjectMapper mapper = new ObjectMapper();

        SeleniumDomExtractor domExtractor = new SeleniumDomExtractor(mapper, configuration.browser);

        return new CatalogService(
                new JsonComponentCatalogRepository(projectRoot, configuration.catalog.directory),
                new SeleniumPageDomCollector(configuration.browser, domExtractor),
                new DomSanitizer(configuration.ai.privacy.dom),
                new OpenAiComponentDiscoveryAgent(aiClient, configuration.catalog.minimumSelectorConfidence));
    }
}

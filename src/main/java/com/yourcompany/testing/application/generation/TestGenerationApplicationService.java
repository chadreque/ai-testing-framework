package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.application.catalog.CatalogService;
import com.yourcompany.testing.application.exception.TestGenerationException;
import com.yourcompany.testing.application.port.FeatureGenerator;
import com.yourcompany.testing.application.port.StepDefinitionGenerator;
import com.yourcompany.testing.application.port.TestCaseInterpreter;
import com.yourcompany.testing.application.port.TestDataPrivacySanitizer;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.PageUrl;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;
import com.yourcompany.testing.domain.source.TestSourceType;
import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.StepCatalog;
import com.yourcompany.testing.domain.testcase.StepGenerationResult;
import com.yourcompany.testing.domain.testcase.TestSpecification;
import com.yourcompany.testing.infrastructure.config.ApplicationConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TestGenerationApplicationService {
    private final TestSourceReader sourceReader;
    private final TestDataPrivacySanitizer testDataSanitizer;
    private final TestCaseInterpreter testCaseInterpreter;
    private final FeatureGenerator featureGenerator;
    private final CatalogService catalogService;
    private final FeatureValidator featureValidator;
    private final FeatureFileService featureFileService;
    private final ExistingStepScanner existingStepScanner;
    private final StepDefinitionGenerator stepDefinitionGenerator;
    private final StepSourceService stepSourceService;
    private final PageUrlExtractor pageUrlExtractor;
    private final ApplicationConfiguration configuration;

    public TestGenerationApplicationService(
            TestSourceReader sourceReader,
            TestDataPrivacySanitizer testDataSanitizer,
            TestCaseInterpreter testCaseInterpreter,
            FeatureGenerator featureGenerator,
            CatalogService catalogService,
            FeatureValidator featureValidator,
            FeatureFileService featureFileService,
            ExistingStepScanner existingStepScanner,
            StepDefinitionGenerator stepDefinitionGenerator,
            StepSourceService stepSourceService,
            PageUrlExtractor pageUrlExtractor,
            ApplicationConfiguration configuration) {
        this.sourceReader = sourceReader;
        this.testDataSanitizer = testDataSanitizer;
        this.testCaseInterpreter = testCaseInterpreter;
        this.featureGenerator = featureGenerator;
        this.catalogService = catalogService;
        this.featureValidator = featureValidator;
        this.featureFileService = featureFileService;
        this.existingStepScanner = existingStepScanner;
        this.stepDefinitionGenerator = stepDefinitionGenerator;
        this.stepSourceService = stepSourceService;
        this.pageUrlExtractor = pageUrlExtractor;
        this.configuration = configuration;
    }

    public GenerationReport generate(TestSource source, Path projectRoot) {
        try {
            TestSourceContent sourceContent = sourceReader.read(source);

            Optional<String> deterministicPageUrl = pageUrlExtractor.extract(sourceContent.content());

            String aiEligibleSource = deterministicPageUrl
                    .map(pageUrl -> sourceContent.content().replace(pageUrl, "[PAGE_URL_REDACTED]"))
                    .orElse(sourceContent.content());
            String sanitizedSource = testDataSanitizer.sanitize(aiEligibleSource);

            TestSpecification interpretedSpecification = testCaseInterpreter.interpret(sanitizedSource);
            TestSpecification testSpecification = resolvePageUrl(interpretedSpecification, deterministicPageUrl);

            Optional<ComponentCatalog> catalog = Optional.empty();

            if (!testSpecification.pageUrl().isBlank()) {
                PageUrl pageUrl = new PageUrl(testSpecification.pageUrl());
                catalog = Optional.of(catalogService.ensureCatalog(pageUrl));
            }

            FeatureModel feature = featureGenerator.generate(testSpecification);
            featureValidator.validate(feature);

            Path featureDirectory = projectRoot.resolve(configuration.generation.featureOutputDirectory).normalize();

            String basePackage = configuration.application.basePackage;
            String generatedStepPackage = basePackage + ".steps";

            Path stepRootDirectory = projectRoot.resolve(configuration.generation.stepOutputDirectory).normalize();
            Path stepDirectory = stepRootDirectory.resolve(generatedStepPackage.replace('.', '/'));

            ensureInsideProject(projectRoot, featureDirectory, "Feature output directory");
            ensureInsideProject(projectRoot, stepDirectory, "Step output directory");

            String stableFeatureIdentity = stableFeatureIdentity(source);

            List<String> scenarioIdentityKeys = scenarioIdentityKeys(source, feature);

            FeatureFileService.FeatureWriteResult featureWriteResult = featureFileService.writeOrMerge(
                    feature, featureDirectory, stableFeatureIdentity, scenarioIdentityKeys);

            String completeFeatureText = Files.readString(featureWriteResult.path());

            StepCatalog existingSteps = existingStepScanner.scan(projectRoot.resolve("src/test/java"));

            List<Path> writtenStepFiles = generateAndWriteSteps(
                    completeFeatureText,
                    existingSteps,
                    catalog,
                    stepDirectory,
                    generatedStepPackage
            );

            List<String> warnings = new ArrayList<>();

            if (featureWriteResult.skippedScenarios() > 0)
                warnings.add(featureWriteResult.skippedScenarios() + " scenario(s) already existed and were preserved without duplication.");

            return new GenerationReport(
                    sourceContent.description(),
                    featureWriteResult.path(),
                    writtenStepFiles,
                    featureWriteResult.addedScenarios(),
                    warnings,
                    catalog.map(ComponentCatalog::pageUrl).orElse("")
            );

        } catch (TestGenerationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TestGenerationException("Test generation failed for source: " + source.value() + ". " + safeMessage(exception), exception);
        }
    }

    private List<Path> generateAndWriteSteps(String featureText, StepCatalog existingSteps, Optional<ComponentCatalog> catalog, Path stepDirectory, String packageName) {
        String validationFeedback = "";

        int maximumAttempts = Math.max(0, configuration.generation.maxStepRepairAttempts) + 1;

        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            try {
                StepGenerationResult generatedSteps = stepDefinitionGenerator.generate(featureText, existingSteps, catalog, validationFeedback);

                List<StepSourceService.PendingJavaSource> pendingSources = stepSourceService.render(generatedSteps, stepDirectory, packageName);

                return stepSourceService.write(pendingSources);
            } catch (RuntimeException exception) {
                if (attempt == maximumAttempts) throw exception;
                validationFeedback = testDataSanitizer.sanitize(safeMessage(exception));
            }
        }

        throw new IllegalStateException("Step generation did not complete");
    }


    private String stableFeatureIdentity(TestSource source) {
        if (source.type() == com.yourcompany.testing.domain.source.TestSourceType.JIRA) return source.value();

        Path sourcePath = Path.of(source.value());

        String fileName = sourcePath.getFileName() == null ? "generated-feature" : sourcePath.getFileName().toString();

        int extensionIndex = fileName.lastIndexOf('.');

        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private List<String> scenarioIdentityKeys(TestSource source, FeatureModel feature) {
        List<String> identities = new ArrayList<>();

        String sourceIdentity = source.type() + ":" +
                (source.type() == TestSourceType.FILE ? Path.of(source.value()).toAbsolutePath().normalize() : source.value());

        for (int scenarioIndex = 0; scenarioIndex < feature.scenarios().size(); scenarioIndex++) {
            identities.add(sourceIdentity + "#scenario-" + scenarioIndex);
        }

        return identities;
    }

    private TestSpecification resolvePageUrl(TestSpecification interpretedSpecification, Optional<String> deterministicPageUrl) {
        if (interpretedSpecification == null) throw new IllegalStateException("AI returned no structured test specification");
        if (deterministicPageUrl.isPresent()) return interpretedSpecification.withPageUrl(deterministicPageUrl.get());
        if (interpretedSpecification.pageUrl().isBlank()) return interpretedSpecification;

        return interpretedSpecification.withPageUrl(new PageUrl(interpretedSpecification.pageUrl()).value());
    }

    private void ensureInsideProject(Path projectRoot, Path outputPath, String label) {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedOutputPath = outputPath.toAbsolutePath().normalize();

        if (!normalizedOutputPath.startsWith(normalizedProjectRoot))
            throw new IllegalStateException(label + " must remain inside the project root: " + normalizedOutputPath);

        Path targetDirectory = normalizedProjectRoot.resolve("target").normalize();

        if (normalizedOutputPath.startsWith(targetDirectory))
            throw new IllegalStateException(label + " must not be inside Maven target/: " + normalizedOutputPath);
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();

        return message.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]");
    }

    public record GenerationReport(
            String source,
            Path featureFile,
            List<Path> stepFiles,
            int addedScenarios,
            List<String> warnings,
            String catalogPageUrl
    ) {
        public GenerationReport {
            stepFiles = List.copyOf(stepFiles);
            warnings = List.copyOf(warnings);
        }
    }
}

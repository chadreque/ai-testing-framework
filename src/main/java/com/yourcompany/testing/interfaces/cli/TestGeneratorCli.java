package com.yourcompany.testing.interfaces.cli;

import com.yourcompany.testing.application.generation.TestGenerationApplicationService;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.infrastructure.bootstrap.ApplicationFactory;

import java.nio.file.Path;

public final class TestGeneratorCli {
    private TestGeneratorCli() {}

    public static void main(String[] arguments) {
        try {
            System.out.println("Processing.\n This won't take a wail, please wait...");

            Command command = Command.parse(arguments);

            Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            System.out.println("Loading folder: " + projectRoot.toString());

            TestGenerationApplicationService generationService = ApplicationFactory.createGenerationService(projectRoot);
            System.out.println("Starting service: " + generationService.getClass().getSimpleName());

            TestGenerationApplicationService.GenerationReport report = generationService.generate(command.source(), projectRoot);
            System.out.println("Starting service: " + report.getClass().getSimpleName() + "\n");

            printReport(report);
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            System.exit(1);
        }
    }

    private static void printReport(TestGenerationApplicationService.GenerationReport report) {
        System.out.println("Generation completed.");
        System.out.println("Source: " + report.source());
        System.out.println("Feature: " + report.featureFile());
        System.out.println("New scenarios: " + report.addedScenarios());
        if (!report.catalogPageUrl().isBlank()) System.out.println("Catalog page: " + report.catalogPageUrl());
        if (report.stepFiles().isEmpty()) {
            System.out.println("Step sources: no changes required");
        } else {
            report.stepFiles().forEach(path -> System.out.println("Step source: " + path));
        }
        report.warnings().forEach(warning -> System.out.println("Warning: " + warning));
    }

    private record Command(TestSource source) {

        private static Command parse(String[] arguments) {
            if (arguments == null || arguments.length != 2) throw usage();

            return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
                case "file" -> new Command(TestSource.file(arguments[1]));
                case "jira" -> new Command(TestSource.jira(arguments[1]));

                default -> throw usage();
            };
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Usage: java -jar ai-testing-framework-2.0.0.jar file <path-to-pdf-doc-docx-or-txt>\n"
                    + "   or: java -jar ai-testing-framework-2.0.0.jar jira <ISSUE-KEY>");
        }
    }
}

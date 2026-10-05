package com.yourcompany.testing.application.generation;

import com.yourcompany.testing.domain.testcase.FeatureModel;
import com.yourcompany.testing.domain.testcase.GherkinStep;
import com.yourcompany.testing.domain.testcase.ScenarioModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFileServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void repeatedGenerationDoesNotDuplicateScenario() throws Exception {
        FeatureFileService service = new FeatureFileService();
        FeatureModel feature = new FeatureModel("Login", List.of(new ScenarioModel(
                "Successful login",
                List.of(
                        new GherkinStep("Given", "I am on the login page"),
                        new GherkinStep("When", "I submit valid credentials"),
                        new GherkinStep("Then", "I reach the dashboard")))));

        FeatureFileService.FeatureWriteResult firstWrite = service.writeOrMerge(feature, temporaryDirectory);
        FeatureFileService.FeatureWriteResult secondWrite = service.writeOrMerge(feature, temporaryDirectory);

        assertEquals(1, firstWrite.addedScenarios());
        assertEquals(0, secondWrite.addedScenarios());
        assertEquals(1, secondWrite.skippedScenarios());
        String featureText = Files.readString(firstWrite.path());
        assertEquals(1, featureText.split("Scenario: Successful login", -1).length - 1);
    }

    @Test
    void newScenarioIsMergedIntoDeterministicFeatureFile() throws Exception {
        FeatureFileService service = new FeatureFileService();
        FeatureModel first = new FeatureModel("Customer Search", List.of(scenario("Search by identifier", "I search by identifier")));
        FeatureModel second = new FeatureModel("Customer Search", List.of(scenario("Search by name", "I search by name")));

        Path firstPath = service.writeOrMerge(first, temporaryDirectory).path();
        Path secondPath = service.writeOrMerge(second, temporaryDirectory).path();

        assertEquals(firstPath, secondPath);
        String content = Files.readString(firstPath);
        assertTrue(content.contains("Scenario: Search by identifier"));
        assertTrue(content.contains("Scenario: Search by name"));
    }

    private ScenarioModel scenario(String name, String whenText) {
        return new ScenarioModel(name, List.of(
                new GherkinStep("Given", "the page is open"),
                new GherkinStep("When", whenText),
                new GherkinStep("Then", "results are displayed")));
    }
}

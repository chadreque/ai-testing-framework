package com.yourcompany.testing.infrastructure.source;

import com.yourcompany.testing.application.exception.SourceReadException;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;
import com.yourcompany.testing.domain.source.TestSourceType;
import com.yourcompany.testing.infrastructure.jira.JiraClient;

public final class JiraTestSourceReader implements TestSourceReader {
    private final JiraClient jiraClient;

    public JiraTestSourceReader(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public boolean supports(TestSource source) {
        return source.type() == TestSourceType.JIRA;
    }

    @Override
    public TestSourceContent read(TestSource source) {
        try {
            JiraClient.JiraIssue issue = jiraClient.getIssue(source.value());
            String content = """
                    SUMMARY:
                    %s

                    DESCRIPTION:
                    %s
                    """.formatted(
                    issue.summary(), issue.description().isBlank() ? "(No description provided)" : issue.description());
            return new TestSourceContent("Jira " + issue.key(), content);
        } catch (Exception exception) {
            throw new SourceReadException("Unable to read Jira test source: " + source.value(), exception);
        }
    }
}

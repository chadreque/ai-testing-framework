package com.yourcompany.testing.application.port;

import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;

public interface TestSourceReader {
    boolean supports(TestSource source);
    TestSourceContent read(TestSource source);
}

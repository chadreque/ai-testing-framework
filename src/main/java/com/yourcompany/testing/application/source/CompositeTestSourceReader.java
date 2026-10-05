package com.yourcompany.testing.application.source;

import com.yourcompany.testing.application.exception.SourceReadException;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;

import java.util.List;

public final class CompositeTestSourceReader implements TestSourceReader {
    private final List<TestSourceReader> readers;

    public CompositeTestSourceReader(List<TestSourceReader> readers) {
        this.readers = List.copyOf(readers);
    }

    @Override
    public boolean supports(TestSource source) {
        return readers.stream().anyMatch(reader -> reader.supports(source));
    }

    @Override
    public TestSourceContent read(TestSource source) {
        return readers.stream()
                .filter(reader -> reader.supports(source))
                .findFirst()
                .orElseThrow(() -> new SourceReadException("Unsupported test source: " + source.value()))
                .read(source);
    }
}

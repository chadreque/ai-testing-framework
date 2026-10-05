package com.yourcompany.testing.infrastructure.source;

import com.yourcompany.testing.application.exception.SourceReadException;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;
import com.yourcompany.testing.domain.source.TestSourceType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class TextTestSourceReader implements TestSourceReader {
    @Override
    public boolean supports(TestSource source) {
        if (source.type() != TestSourceType.FILE) return false;
        String fileName = source.value().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".txt");
    }

    @Override
    public TestSourceContent read(TestSource source) {
        Path path = validateFile(source.value());
        try {
            return new TestSourceContent(path.toString(), Files.readString(path));
        } catch (Exception exception) {
            throw new SourceReadException("Unable to read text test source: " + path, exception);
        }
    }

    private Path validateFile(String value) {
        Path path = Path.of(value);
        if (!Files.isRegularFile(path)) throw new SourceReadException("Test source file does not exist: " + path);
        return path;
    }
}

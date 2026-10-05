package com.yourcompany.testing.infrastructure.source;

import com.yourcompany.testing.application.exception.SourceReadException;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;
import com.yourcompany.testing.domain.source.TestSourceType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class PdfTestSourceReader implements TestSourceReader {
    @Override
    public boolean supports(TestSource source) {
        return source.type() == TestSourceType.FILE && source.value().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public TestSourceContent read(TestSource source) {
        Path path = Path.of(source.value());
        if (!Files.isRegularFile(path)) throw new SourceReadException("Test source file does not exist: " + path);
        try (var document = Loader.loadPDF(path.toFile())) {
            String extractedText = new PDFTextStripper().getText(document);
            if (extractedText == null || extractedText.isBlank()) {
                throw new SourceReadException("PDF contains no extractable text: " + path + ". Scanned/image-only PDFs require OCR before using this generator.");
            }
            return new TestSourceContent(path.toString(), extractedText);
        } catch (SourceReadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SourceReadException("Unable to read PDF test source: " + path, exception);
        }
    }
}

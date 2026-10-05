package com.yourcompany.testing.infrastructure.source;

import com.yourcompany.testing.application.exception.SourceReadException;
import com.yourcompany.testing.application.port.TestSourceReader;
import com.yourcompany.testing.domain.source.TestSource;
import com.yourcompany.testing.domain.source.TestSourceContent;
import com.yourcompany.testing.domain.source.TestSourceType;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class WordTestSourceReader implements TestSourceReader {
    @Override
    public boolean supports(TestSource source) {
        if (source.type() != TestSourceType.FILE) return false;
        String fileName = source.value().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".docx") || fileName.endsWith(".doc");
    }

    @Override
    public TestSourceContent read(TestSource source) {
        Path path = Path.of(source.value());
        if (!Files.isRegularFile(path)) throw new SourceReadException("Test source file does not exist: " + path);
        try {
            String content = path.toString().toLowerCase(Locale.ROOT).endsWith(".docx") ? readDocx(path) : readDoc(path);
            if (content.isBlank()) throw new SourceReadException("Word document contains no extractable test content: " + path);
            return new TestSourceContent(path.toString(), content);
        } catch (SourceReadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SourceReadException("Unable to read Word test source: " + path, exception);
        }
    }

    private String readDocx(Path path) throws Exception {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(inputStream)) {
            document.getParagraphs().forEach(paragraph -> append(content, paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells().forEach(cell -> append(content, cell.getText()))));
        }
        return content.toString();
    }

    private String readDoc(Path path) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private void append(StringBuilder target, String text) {
        if (text != null && !text.isBlank()) target.append(text.trim()).append(System.lineSeparator());
    }
}

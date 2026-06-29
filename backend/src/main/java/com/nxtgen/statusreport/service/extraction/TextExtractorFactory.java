package com.nxtgen.statusreport.service.extraction;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class TextExtractorFactory {

    private final List<DocumentTextExtractor> extractors;

    public TextExtractorFactory(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public String extractText(String originalFilename, InputStream inputStream) throws IOException {
        for (DocumentTextExtractor extractor : extractors) {
            if (extractor.supports(originalFilename)) {
                return extractor.extract(inputStream);
            }
        }
        throw new IllegalArgumentException(
                "Unsupported file type for '" + originalFilename + "'. Only .docx and .pdf are supported.");
    }
}

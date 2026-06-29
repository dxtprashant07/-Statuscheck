package com.nxtgen.statusreport.service.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    private final PdfOcrService ocrService;

    public PdfTextExtractor(PdfOcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public boolean supports(String originalFilename) {
        return originalFilename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();

        String text;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }

        if (text != null && !text.isBlank()) {
            return text;
        }

        // No embedded text layer - this is a scanned/image-only PDF. Fall back
        // to OCR so the content can still feed the extraction pipeline.
        if (ocrService.isEnabled()) {
            log.info("PDF has no extractable text layer; falling back to OCR");
            return ocrService.ocr(bytes);
        }

        log.warn("PDF has no extractable text layer and OCR is disabled; returning empty text");
        return "";
    }
}

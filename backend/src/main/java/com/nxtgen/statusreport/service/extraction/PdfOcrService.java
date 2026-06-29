package com.nxtgen.statusreport.service.extraction;

import com.nxtgen.statusreport.config.OcrProperties;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Optical character recognition fallback for scanned, image-only PDFs that
 * carry no extractable text layer. Each page is rendered to a bitmap with
 * PDFBox and handed to Tesseract (via Tess4J). Tess4J ships the native Tesseract
 * libraries, so only the {@code <language>.traineddata} files need to be present
 * at runtime (see {@link OcrProperties}).
 */
@Service
public class PdfOcrService {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrService.class);

    private final OcrProperties properties;

    public PdfOcrService(OcrProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Runs OCR over every page of the given PDF and returns the concatenated
     * text. A {@link Tesseract} instance is created per call because it is not
     * thread-safe and uploads may be handled concurrently.
     */
    public String ocr(byte[] pdfBytes) throws IOException {
        if (!properties.enabled()) {
            throw new IllegalStateException(
                    "This PDF has no text layer and OCR is disabled. Set ocr.enabled=true to extract it.");
        }

        ITesseract tesseract = new Tesseract();
        if (properties.tessdataPath() != null && !properties.tessdataPath().isBlank()) {
            tesseract.setDatapath(properties.tessdataPath());
        }
        tesseract.setLanguage(properties.language());

        StringBuilder text = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.info("Running OCR on {} page(s) at {} DPI (language={})",
                    pageCount, properties.dpi(), properties.language());

            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, properties.dpi(), ImageType.RGB);
                try {
                    text.append(tesseract.doOCR(image)).append("\n\n");
                } catch (TesseractException e) {
                    throw new IllegalStateException(
                            "OCR failed on page " + (page + 1) + ": " + e.getMessage()
                                    + ". Ensure '" + properties.language()
                                    + ".traineddata' is available (set ocr.tessdata-path or TESSDATA_PREFIX).", e);
                }
            }
        }
        return text.toString();
    }
}

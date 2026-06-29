package com.nxtgen.statusreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the scanned-PDF OCR fallback (see PdfOcrService).
 *
 * @param enabled      whether OCR runs when a PDF has no extractable text layer
 * @param tessdataPath folder containing the {@code <language>.traineddata} files;
 *                     blank lets Tesseract use its TESSDATA_PREFIX default
 * @param language     Tesseract language code(s), e.g. {@code eng} or {@code eng+hin}
 * @param dpi          render resolution for each PDF page before OCR
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(
        boolean enabled,
        String tessdataPath,
        String language,
        int dpi
) {
    public OcrProperties {
        if (language == null || language.isBlank()) {
            language = "eng";
        }
        if (dpi <= 0) {
            dpi = 300;
        }
    }
}

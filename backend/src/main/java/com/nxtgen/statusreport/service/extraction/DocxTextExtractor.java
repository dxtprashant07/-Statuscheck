package com.nxtgen.statusreport.service.extraction;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class DocxTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(String originalFilename) {
        String lower = originalFilename.toLowerCase();
        return lower.endsWith(".docx");
    }

    @Override
    public String extract(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            // Walk body elements in document order so paragraphs AND tables are
            // captured. document.getParagraphs() alone silently drops every table,
            // losing data that often lives in tabular status/module listings.
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(text, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
            return text.toString();
        }
    }

    private void appendParagraph(StringBuilder text, XWPFParagraph paragraph) {
        String content = paragraph.getText();
        if (content != null && !content.isBlank()) {
            text.append(content).append("\n");
        }
    }

    /**
     * Flattens a table to one line per row with cells separated by " | ", which
     * preserves enough structure for the rule/LLM extractors to read each row as
     * a work item (e.g. "Module | Status | Date").
     */
    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder line = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (line.length() > 0) {
                    line.append(" | ");
                }
                line.append(cellText == null ? "" : cellText.trim());
            }
            // Skip blank rows (all cells empty / only separators).
            if (!line.toString().replace("|", "").isBlank()) {
                text.append(line).append("\n");
            }
        }
        text.append("\n");
    }
}

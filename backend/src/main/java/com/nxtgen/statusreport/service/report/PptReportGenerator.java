package com.nxtgen.statusreport.service.report;

import com.nxtgen.statusreport.model.ComparisonResult;
import com.nxtgen.statusreport.model.ItemStatus;
import com.nxtgen.statusreport.model.Project;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a presentation-grade 16:9 deck: a branded cover, a summary slide with
 * KPI tiles and a stacked status-distribution bar, and per-status detail slides
 * with chips, evidence and footers/page numbers.
 */
@Component
public class PptReportGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final String FONT = "Calibri";
    private static final String FONT_LIGHT = "Calibri Light";

    private static final Color BRAND = new Color(0x12355B);
    private static final Color ACCENT = new Color(0x2C7A7B);
    private static final Color WHITE = Color.WHITE;
    private static final Color INK = new Color(0x1A1A1A);
    private static final Color MUTED = new Color(0x8A93A2);
    private static final Color TRACK = new Color(0xE5E9EF);
    private static final Color HAIRLINE = new Color(0xE2E6EC);

    private static final int W = 960;
    private static final int H = 540;
    private static final int MARGIN = 56;
    private static final int ITEMS_PER_SLIDE = 6;

    private static final String DEFAULT_MODULE = "General";

    public byte[] generate(Project project, List<ComparisonResult> results) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new Dimension(W, H));
            int[] page = {0};
            addCoverSlide(ppt, project, reportingPeriod(results));
            addSummarySlide(ppt, project, results, page);
            for (Map.Entry<String, List<ComparisonResult>> entry : groupByModule(results).entrySet()) {
                addModuleSlides(ppt, project, entry.getKey(), entry.getValue(), page);
            }
            ppt.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------- cover

    private void addCoverSlide(XMLSlideShow ppt, Project project, String period) {
        XSLFSlide slide = ppt.createSlide();
        rect(slide, 0, 0, W, H, BRAND);
        rect(slide, MARGIN, 250, 64, 5, ACCENT);

        XSLFTextBox kicker = box(slide, MARGIN, 170, W - 2 * MARGIN, 28);
        run(firstPara(kicker), "PROJECT STATUS REPORT", 15, true, new Color(0x8FB3C9), FONT);

        XSLFTextBox title = box(slide, MARGIN, 268, W - 2 * MARGIN, 110);
        run(firstPara(title), project.getName(), 42, true, WHITE, FONT_LIGHT);

        XSLFTextBox sub = box(slide, MARGIN, 390, W - 2 * MARGIN, 70);
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            run(firstPara(sub), project.getDescription(), 15, false, new Color(0xB8C6D6), FONT);
        }
        XSLFTextBox date = box(slide, MARGIN, H - 58, W - 2 * MARGIN, 24);
        String prefix = period != null ? "Reporting period  " + period + "       ·       " : "";
        run(firstPara(date), prefix + "Generated " + DATE_FORMAT.format(LocalDate.now()),
                12, false, new Color(0x7E93A8), FONT);
    }

    // -------------------------------------------------------------- summary

    private void addSummarySlide(XMLSlideShow ppt, Project project, List<ComparisonResult> results, int[] page) {
        XSLFSlide slide = ppt.createSlide();
        heading(slide, "Summary");

        int total = results.size();
        long completed = countOf(results, ItemStatus.COMPLETED);
        int pct = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        XSLFTextBox intro = box(slide, MARGIN, 96, W - 2 * MARGIN, 30);
        run(firstPara(intro),
                total == 0 ? "No comparison data yet — run a comparison to populate this deck."
                        : total + " items tracked   ·   " + completed + " completed (" + pct + "%)",
                15, false, MUTED, FONT);

        // KPI tiles
        ItemStatus[] statuses = ItemStatus.values();
        int n = statuses.length;
        int gap = 16;
        int tileW = (W - 2 * MARGIN - (n - 1) * gap) / n;
        int tileH = 130;
        int y = 150;
        for (int i = 0; i < n; i++) {
            ItemStatus status = statuses[i];
            int x = MARGIN + i * (tileW + gap);
            rect(slide, x, y, tileW, tileH, new Color(0xF6F8FA));
            rect(slide, x, y, tileW, 4, accent(status));          // accent strip on top

            XSLFTextBox count = box(slide, x, y + 26, tileW, 56);
            XSLFTextParagraph cp = firstPara(count);
            cp.setTextAlign(TextAlign.CENTER);
            run(cp, String.valueOf(countOf(results, status)), 34, true, accent(status), FONT_LIGHT);

            XSLFTextBox label = box(slide, x, y + 88, tileW, 28);
            XSLFTextParagraph lp = firstPara(label);
            lp.setTextAlign(TextAlign.CENTER);
            run(lp, formatStatus(status).toUpperCase(), 10, true, MUTED, FONT);
        }

        // Stacked distribution bar
        int barY = 330;
        int barW = W - 2 * MARGIN;
        XSLFTextBox barLabel = box(slide, MARGIN, barY - 30, barW, 22);
        run(firstPara(barLabel), "DISTRIBUTION", 10, true, MUTED, FONT);
        rect(slide, MARGIN, barY, barW, 30, TRACK);
        if (total > 0) {
            int x = MARGIN;
            for (ItemStatus status : statuses) {
                long count = countOf(results, status);
                if (count == 0) {
                    continue;
                }
                int segW = (int) Math.round((double) count / total * barW);
                if (x + segW > MARGIN + barW) {
                    segW = MARGIN + barW - x;
                }
                rect(slide, x, barY, segW, 30, accent(status));
                x += segW;
            }
        }

        footer(slide, project, ++page[0]);
    }

    // --------------------------------------------------------------- detail

    private void addModuleSlides(XMLSlideShow ppt, Project project, String module,
                                 List<ComparisonResult> items, int[] page) {
        long completed = countOf(items, ItemStatus.COMPLETED);
        int pct = items.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / items.size());

        for (int start = 0; start < items.size(); start += ITEMS_PER_SLIDE) {
            int end = Math.min(start + ITEMS_PER_SLIDE, items.size());
            XSLFSlide slide = ppt.createSlide();

            rect(slide, 0, 0, W, 78, BRAND);
            rect(slide, 0, 0, 6, 78, ACCENT);                       // module accent edge
            XSLFTextBox titleBox = box(slide, MARGIN, 14, W - 2 * MARGIN, 28);
            titleBox.setVerticalAlignment(VerticalAlignment.MIDDLE);
            String label = module + (items.size() > ITEMS_PER_SLIDE ? "  (cont.)" : "");
            run(firstPara(titleBox), label, 22, true, WHITE, FONT_LIGHT);
            XSLFTextBox metaBox = box(slide, MARGIN, 44, W - 2 * MARGIN, 22);
            run(firstPara(metaBox), items.size() + (items.size() == 1 ? " item" : " items")
                    + "   ·   " + pct + "% complete", 11, false, new Color(0xB8C6D6), FONT);

            int y = 110;
            int rowH = 60;
            int statusW = 150;
            for (int i = start; i < end; i++) {
                ComparisonResult result = items.get(i);
                ItemStatus status = result.getStatus();
                rect(slide, MARGIN, y + 6, 4, rowH - 16, accent(status));   // accent tick by status

                XSLFTextBox itemBox = box(slide, MARGIN + 16, y, W - 2 * MARGIN - 16 - statusW, rowH);
                XSLFTextParagraph titlePara = firstPara(itemBox);
                run(titlePara, result.getProposalItem().getTitle(), 15, true, INK, FONT);

                String evidence = result.getEvidence() != null && !result.getEvidence().isBlank()
                        ? result.getEvidence() : "Not mentioned in the status report";
                XSLFTextParagraph evPara = itemBox.addNewTextParagraph();
                evPara.setSpaceBefore(3.0);
                run(evPara, evidence, 11.5, false, MUTED, FONT);

                XSLFTextBox statusBox = box(slide, W - MARGIN - statusW, y + 4, statusW, 22);
                XSLFTextParagraph sp = firstPara(statusBox);
                sp.setTextAlign(TextAlign.RIGHT);
                run(sp, formatStatus(status).toUpperCase(), 11, true, accent(status), FONT);

                rect(slide, MARGIN, y + rowH, W - 2 * MARGIN, 1, HAIRLINE);   // divider
                y += rowH + 6;
            }

            footer(slide, project, ++page[0]);
        }
    }

    /** Reporting window derived from the earliest and latest planned dates across
     * the tracked items; null when no item carries a planned date. */
    private String reportingPeriod(List<ComparisonResult> results) {
        LocalDate start = null;
        LocalDate end = null;
        for (ComparisonResult r : results) {
            LocalDate d = r.getProposalItem().getPlannedDate();
            if (d == null) {
                continue;
            }
            if (start == null || d.isBefore(start)) {
                start = d;
            }
            if (end == null || d.isAfter(end)) {
                end = d;
            }
        }
        if (start == null) {
            return null;
        }
        return start.equals(end)
                ? DATE_FORMAT.format(start)
                : DATE_FORMAT.format(start) + "  –  " + DATE_FORMAT.format(end);
    }

    /** Buckets results by their item's module, defaulting a missing module to
     * {@value #DEFAULT_MODULE}. Named modules come first (alphabetically); the
     * "General" catch-all is always rendered last. */
    private Map<String, List<ComparisonResult>> groupByModule(List<ComparisonResult> results) {
        Map<String, List<ComparisonResult>> byModule = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ComparisonResult r : results) {
            String module = r.getProposalItem().getModule();
            String key = (module == null || module.isBlank()) ? DEFAULT_MODULE : module.strip();
            byModule.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        if (byModule.size() > 1 && byModule.containsKey(DEFAULT_MODULE)) {
            List<ComparisonResult> general = byModule.remove(DEFAULT_MODULE);
            Map<String, List<ComparisonResult>> ordered = new LinkedHashMap<>(byModule);
            ordered.put(DEFAULT_MODULE, general);
            return ordered;
        }
        return byModule;
    }

    // -------------------------------------------------------- shared chrome

    private void heading(XSLFSlide slide, String text) {
        rect(slide, MARGIN, 44, 5, 30, ACCENT);
        XSLFTextBox box = box(slide, MARGIN + 16, 40, W - 2 * MARGIN, 40);
        run(firstPara(box), text, 24, true, BRAND, FONT_LIGHT);
    }

    private void footer(XSLFSlide slide, Project project, int pageNo) {
        rect(slide, MARGIN, H - 34, W - 2 * MARGIN, 1, HAIRLINE);
        XSLFTextBox left = box(slide, MARGIN, H - 30, (W - 2 * MARGIN) / 2, 22);
        run(firstPara(left), project.getName(), 9, false, MUTED, FONT);
        XSLFTextBox right = box(slide, W / 2, H - 30, W / 2 - MARGIN, 22);
        XSLFTextParagraph rp = firstPara(right);
        rp.setTextAlign(TextAlign.RIGHT);
        run(rp, "Page " + pageNo, 9, false, MUTED, FONT);
    }

    // ---------------------------------------------------------- primitives

    private XSLFAutoShape rect(XSLFSlide slide, int x, int y, int w, int h, Color fill) {
        XSLFAutoShape shape = slide.createAutoShape();
        shape.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
        shape.setAnchor(new Rectangle(x, y, w, h));
        shape.setFillColor(fill);
        shape.setLineColor(null);
        return shape;
    }

    private XSLFTextBox box(XSLFSlide slide, int x, int y, int w, int h) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, h));
        box.setLeftInset(0);
        box.setRightInset(0);
        box.setTopInset(2);
        box.setBottomInset(2);
        return box;
    }

    private void run(XSLFTextParagraph para, String text, double size, boolean bold, Color color, String font) {
        XSLFTextRun r = para.addNewTextRun();
        r.setText(text);
        r.setFontSize(size);
        r.setBold(bold);
        r.setFontColor(color);
        r.setFontFamily(font);
    }

    private XSLFTextParagraph firstPara(XSLFTextShape shape) {
        return shape.getTextParagraphs().isEmpty() ? shape.addNewTextParagraph() : shape.getTextParagraphs().get(0);
    }

    private long countOf(List<ComparisonResult> results, ItemStatus status) {
        return results.stream().filter(r -> r.getStatus() == status).count();
    }

    private Color accent(ItemStatus status) {
        return switch (status) {
            case COMPLETED -> new Color(0x2E7D32);
            case IN_PROGRESS -> new Color(0x1565C0);
            case PENDING -> new Color(0xB7791F);
            case AT_RISK -> new Color(0xC62828);
            case NOT_STARTED -> new Color(0x5F6B7A);
        };
    }

    private String formatStatus(ItemStatus status) {
        return switch (status) {
            case COMPLETED -> "Completed";
            case IN_PROGRESS -> "In progress";
            case PENDING -> "Pending";
            case AT_RISK -> "At risk";
            case NOT_STARTED -> "Not started";
        };
    }
}

package com.nxtgen.statusreport.service.report;

import com.nxtgen.statusreport.model.ComparisonResult;
import com.nxtgen.statusreport.model.ItemStatus;
import com.nxtgen.statusreport.model.Project;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

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
 * Renders the status report in the narrative, milestone-sectioned style of a
 * conventional monthly project status report (cf. the SAHAS 2.0 sample): a
 * centred title block, a project-details block, and milestone sections
 * (Achieved / Under Execution / Next / Points Needing Attention) where each
 * item reads as a "<b>Feature:</b> description" line, grouped by module.
 */
@Component
public class WordReportGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private static final String FONT = "Calibri";
    private static final String FONT_LIGHT = "Calibri Light";
    private static final String BRAND = "12355B";   // deep navy
    private static final String ACCENT = "2C7A7B";  // teal
    private static final String INK = "1A1A1A";
    private static final String MUTED = "6B7280";

    private static final String DEFAULT_MODULE = "General";

    public byte[] generate(Project project, List<ComparisonResult> results) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            LocalDate[] period = reportingPeriod(results);

            addTitleBlock(document, project, period);
            addProjectDetails(document, project, period, results);

            addMilestoneSection(document, "Milestone Achieved",
                    select(results, ItemStatus.COMPLETED));
            addMilestoneSection(document, "Milestone Under Execution",
                    select(results, ItemStatus.IN_PROGRESS));
            addMilestoneSection(document, "Next Milestone",
                    select(results, ItemStatus.PENDING, ItemStatus.NOT_STARTED));
            addMilestoneSection(document, "Points Needing Attention",
                    select(results, ItemStatus.AT_RISK));

            document.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------ title block

    private void addTitleBlock(XWPFDocument document, Project project, LocalDate[] period) {
        centered(document, "Project Status", 22, true, BRAND, FONT_LIGHT, 40, 0);
        centered(document, project.getName(), 15, true, INK, FONT, 0, 0);
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            centered(document, project.getDescription(), 11, false, MUTED, FONT, 0, 0);
        }
        if (period != null) {
            String range = DATE_FORMAT.format(period[0]) + " to " + DATE_FORMAT.format(period[1]);
            centered(document, range, 11, true, ACCENT, FONT, 60, 0);
        }
        centered(document, "Generated " + DATE_FORMAT.format(LocalDate.now()),
                9, false, MUTED, FONT, 40, 240);
    }

    // -------------------------------------------------------- project details

    private void addProjectDetails(XWPFDocument document, Project project,
                                   LocalDate[] period, List<ComparisonResult> results) {
        sectionHeading(document, "Project Details");

        detailLine(document, "Project Name", project.getName());
        if (period != null) {
            detailLine(document, "Reporting Period",
                    DATE_FORMAT.format(period[0]) + " to " + DATE_FORMAT.format(period[1]));
        }
        int total = results.size();
        long completed = count(results, ItemStatus.COMPLETED);
        int pct = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        detailLine(document, "Items Tracked", String.valueOf(total));
        detailLine(document, "Completed", completed + " of " + total + " (" + pct + "%)");
    }

    // ----------------------------------------------------------- milestones

    private void addMilestoneSection(XWPFDocument document, String heading, List<ComparisonResult> items) {
        sectionHeading(document, heading);

        if (items.isEmpty()) {
            XWPFParagraph p = document.createParagraph();
            p.setSpacingAfter(80);
            XWPFRun r = p.createRun();
            r.setText("None reported for this period.");
            r.setItalic(true);
            r.setFontFamily(FONT);
            r.setFontSize(10.5);
            r.setColor(MUTED);
            return;
        }

        Map<String, List<ComparisonResult>> byModule = groupByModule(items);
        boolean showModuleHeadings = byModule.size() > 1;
        for (Map.Entry<String, List<ComparisonResult>> entry : byModule.entrySet()) {
            if (showModuleHeadings) {
                moduleHeading(document, entry.getKey());
            }
            for (ComparisonResult result : entry.getValue()) {
                itemLine(document, result);
            }
        }
    }

    /** One "<b>Feature:</b> description" paragraph, mirroring the sample report. */
    private void itemLine(XWPFDocument document, ComparisonResult result) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setSpacingAfter(100);
        p.setIndentationLeft(180);
        p.setIndentationHanging(180);

        XWPFRun lead = p.createRun();
        lead.setText("•  " + result.getProposalItem().getTitle() + ": ");
        lead.setBold(true);
        lead.setFontFamily(FONT);
        lead.setFontSize(10.5);
        lead.setColor(INK);

        XWPFRun body = p.createRun();
        body.setText(itemBody(result));
        body.setFontFamily(FONT);
        body.setFontSize(10.5);
        body.setColor(INK);
    }

    private String itemBody(ComparisonResult result) {
        if (result.getEvidence() != null && !result.getEvidence().isBlank()) {
            return result.getEvidence().strip();
        }
        String description = result.getProposalItem().getDescription();
        if (description != null && !description.isBlank()) {
            return description.strip();
        }
        return result.getStatus() == ItemStatus.NOT_STARTED
                ? "Not yet reported in the status report."
                : "No further detail provided.";
    }

    // ------------------------------------------------------------- utilities

    private void centered(XWPFDocument document, String text, double size, boolean bold,
                          String color, String font, int before, int after) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontFamily(font);
        r.setFontSize(size);
        r.setColor(color);
    }

    private void sectionHeading(XWPFDocument document, String text) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(280);
        heading.setSpacingAfter(120);
        heading.setBorderBottom(Borders.SINGLE);
        XWPFRun run = heading.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontFamily(FONT);
        run.setFontSize(13);
        run.setColor(BRAND);
    }

    private void moduleHeading(XWPFDocument document, String module) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingBefore(140);
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setText(module);
        r.setBold(true);
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setColor(ACCENT);
    }

    private void detailLine(XWPFDocument document, String label, String value) {
        XWPFParagraph p = document.createParagraph();
        p.setSpacingAfter(40);
        XWPFRun l = p.createRun();
        l.setText(label + ": ");
        l.setBold(true);
        l.setFontFamily(FONT);
        l.setFontSize(10.5);
        l.setColor(INK);
        XWPFRun v = p.createRun();
        v.setText(value);
        v.setFontFamily(FONT);
        v.setFontSize(10.5);
        v.setColor(INK);
    }

    private List<ComparisonResult> select(List<ComparisonResult> results, ItemStatus... statuses) {
        List<ItemStatus> wanted = List.of(statuses);
        List<ComparisonResult> out = new ArrayList<>();
        for (ComparisonResult r : results) {
            if (wanted.contains(r.getStatus())) {
                out.add(r);
            }
        }
        return out;
    }

    private long count(List<ComparisonResult> results, ItemStatus status) {
        return results.stream().filter(r -> r.getStatus() == status).count();
    }

    /** Reporting window from the earliest and latest planned dates; null if none. */
    private LocalDate[] reportingPeriod(List<ComparisonResult> results) {
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
        return start == null ? null : new LocalDate[]{start, end};
    }

    /** Buckets results by module; named modules first (alphabetical), "General" last. */
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
}

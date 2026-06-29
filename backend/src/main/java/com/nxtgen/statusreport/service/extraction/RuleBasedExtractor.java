package com.nxtgen.statusreport.service.extraction;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pass 1 of the hybrid extraction pipeline. Templated documents (numbered
 * milestones, bullet lists of deliverables) are handled here for free.
 * Anything that doesn't match a known structural pattern is returned as
 * "leftover" text for {@link LlmExtractor} to interpret.
 */
@Component
public class RuleBasedExtractor {

    private static final Pattern BULLET_LINE = Pattern.compile("^\\s*[•\\-*]\\s+(.+)$");
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*\\d+[.)]\\s+(.+)$");

    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern TEXT_DATE = Pattern.compile(
            "\\b(\\d{1,2}\\s+)?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);

    /** Explicit section labels such as "Module: Authentication" or "Workstream - Payments". */
    private static final Pattern EXPLICIT_SECTION = Pattern.compile(
            "^(?:module|section|workstream|component|phase|area|epic)\\s*[:\\-–]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    public RuleExtractionResult extract(String rawText) {
        List<ExtractedItem> items = new ArrayList<>();
        List<String> leftoverLines = new ArrayList<>();
        String currentModule = null;

        for (String line : rawText.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            String content = matchListLine(line);
            if (content != null) {
                // List items inherit the module of the heading they sit under.
                items.add(toExtractedItem(content, currentModule));
                continue;
            }
            String heading = asSectionHeading(line);
            if (heading != null) {
                // A standalone heading is a section marker, not a work item.
                currentModule = heading;
                continue;
            }
            leftoverLines.add(line.trim());
        }

        return new RuleExtractionResult(items, String.join("\n", leftoverLines));
    }

    /**
     * Recognises a non-list line as a section heading whose name becomes the
     * module for the bullets that follow. Matches either an explicit
     * "Module: X" style label, or a short, title-like standalone line. Returns
     * null for ordinary prose so it still flows to the LLM pass untouched.
     */
    private String asSectionHeading(String line) {
        String trimmed = line.trim();

        Matcher explicit = EXPLICIT_SECTION.matcher(trimmed);
        if (explicit.matches()) {
            return cleanHeading(explicit.group(1));
        }

        boolean colonTerminated = trimmed.endsWith(":");
        if (colonTerminated) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty() || trimmed.length() > 60) {
            return null;
        }
        // A heading is a short noun phrase: no sentence-ending punctuation and
        // not too many words.
        if (trimmed.matches(".*[.!?,;].*")) {
            return null;
        }
        if (!trimmed.matches(".*[A-Za-z].*")) {
            return null;
        }
        String[] words = trimmed.split("\\s+");
        if (words.length > 7) {
            return null;
        }
        // Accept when it clearly reads like a heading: a trailing colon, ALL CAPS,
        // or Title Case across its significant words. Otherwise treat as prose.
        if (colonTerminated || isAllCaps(trimmed) || isTitleCase(words)) {
            return cleanHeading(trimmed);
        }
        return null;
    }

    private boolean isAllCaps(String text) {
        return text.equals(text.toUpperCase()) && text.matches(".*[A-Z].*");
    }

    private boolean isTitleCase(String[] words) {
        int significant = 0;
        int capitalized = 0;
        for (String word : words) {
            if (word.length() < 3) {
                continue; // skip connectors like "of", "to", "&"
            }
            significant++;
            if (Character.isUpperCase(word.charAt(0))) {
                capitalized++;
            }
        }
        return significant > 0 && capitalized == significant;
    }

    private String cleanHeading(String text) {
        String cleaned = text.trim().replaceAll("[\\s:\\-–]+$", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String matchListLine(String line) {
        Matcher bullet = BULLET_LINE.matcher(line);
        if (bullet.matches()) {
            return bullet.group(1).trim();
        }
        Matcher numbered = NUMBERED_LINE.matcher(line);
        if (numbered.matches()) {
            return numbered.group(1).trim();
        }
        return null;
    }

    private ExtractedItem toExtractedItem(String content, String module) {
        LocalDate date = findDate(content);
        // Title is the line up to the first colon or dash separator (common in
        // "Deliverable: Vendor API integration" style lines); else the whole line,
        // capped to keep titles short and descriptions in the description field.
        String title = content;
        String description = null;
        int separator = content.indexOf(':');
        if (separator > 0 && separator < 80) {
            title = content.substring(0, separator).trim();
            description = content.substring(separator + 1).trim();
        } else if (content.length() > 90) {
            title = content.substring(0, 90).trim();
            description = content;
        }
        return ExtractedItem.byRule(title, description, date, module);
    }

    private LocalDate findDate(String text) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group());
            } catch (DateTimeParseException ignored) {
                // fall through to other patterns
            }
        }
        Matcher slash = SLASH_DATE.matcher(text);
        if (slash.find()) {
            int day = Integer.parseInt(slash.group(1));
            int month = Integer.parseInt(slash.group(2));
            int year = normalizeYear(slash.group(3));
            try {
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                // ambiguous day/month order or invalid date - skip
            }
        }
        Matcher text2 = TEXT_DATE.matcher(text);
        if (text2.find()) {
            try {
                String day = text2.group(1) != null ? text2.group(1).trim() : "1";
                String normalized = day + " " + text2.group(2) + " " + text2.group(3);
                return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
                // unparseable month abbreviation - skip
            }
        }
        return null;
    }

    private int normalizeYear(String yearText) {
        int year = Integer.parseInt(yearText);
        return year < 100 ? 2000 + year : year;
    }
}

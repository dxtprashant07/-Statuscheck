package com.nxtgen.statusreport.service.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.statusreport.client.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of the hybrid extraction pipeline. Only called with the leftover
 * text {@link RuleBasedExtractor} could not confidently parse - free-form
 * narrative paragraphs that describe tasks, risks or progress in prose.
 *
 * <p>Large reports are split into chunks before being sent to the model so a
 * single reply never has to enumerate so many items that it overruns the
 * provider's max-tokens budget and gets cut off mid-array. As a second line of
 * defence, {@link #parseItems(String)} salvages every complete object it can
 * from a reply that still came back truncated, rather than discarding the whole
 * batch over one incomplete trailing item.
 */
@Component
public class LlmExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmExtractor.class);

    /**
     * Rough upper bound on characters of narrative sent to the model per call.
     * Keeping each chunk modest bounds how many items a single reply must list
     * (so the JSON response stays inside the max-tokens budget) and keeps each
     * request's token count well under the provider's per-request / per-minute
     * limits. ~4000 chars is roughly 1000-1300 tokens.
     */
    private static final int MAX_CHUNK_CHARS = 4000;

    private static final String SYSTEM_PROMPT = """
            You extract project work items (tasks, milestones, deliverables, risks)
            from narrative project text. Respond with ONLY a JSON array, no prose,
            no markdown code fences. Each element must have exactly these fields:
            {"title": string, "description": string or null, "plannedDate": "YYYY-MM-DD" or null, "module": string or null}.
            "module" is the functional area, component or workstream the item belongs
            to (for example "Authentication", "Reporting", "Infrastructure", "Payments").
            Infer it from nearby section headings or the item's subject; use a short
            noun phrase and reuse the same wording for items in the same area. Set it
            to null only when no sensible module can be inferred.
            If the text contains no identifiable work items, respond with an empty array: []
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmExtractor(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public List<ExtractedItem> extract(String leftoverText) {
        if (leftoverText == null || leftoverText.isBlank()) {
            return List.of();
        }

        // Merge across chunks, de-duplicating by title (case-insensitive) so the
        // same item described in two adjacent paragraphs isn't listed twice.
        Map<String, ExtractedItem> byTitle = new LinkedHashMap<>();
        for (String chunk : chunk(leftoverText)) {
            String reply = llmClient.complete(SYSTEM_PROMPT, chunk);
            for (ExtractedItem item : parseItems(reply)) {
                byTitle.putIfAbsent(item.title().strip().toLowerCase(), item);
            }
        }
        return new ArrayList<>(byTitle.values());
    }

    /**
     * Splits the text into chunks no larger than {@link #MAX_CHUNK_CHARS}.
     * Paragraphs (blank-line separated) are packed together up to the budget;
     * a single paragraph larger than the budget - common with OCR output, which
     * often has few blank lines - is hard-split so no one request ever exceeds
     * the provider's token limits.
     */
    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\r?\\n\\s*\\r?\\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            for (String piece : splitToBudget(p)) {
                if (current.length() > 0 && current.length() + piece.length() + 2 > MAX_CHUNK_CHARS) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(piece);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty()) {
            chunks.add(text);
        }
        return chunks;
    }

    /**
     * Breaks a single over-budget paragraph into {@link #MAX_CHUNK_CHARS}-sized
     * pieces, preferring to cut on whitespace so words aren't split mid-token.
     */
    private List<String> splitToBudget(String paragraph) {
        if (paragraph.length() <= MAX_CHUNK_CHARS) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        int i = 0;
        while (i < paragraph.length()) {
            int end = Math.min(i + MAX_CHUNK_CHARS, paragraph.length());
            if (end < paragraph.length()) {
                int ws = paragraph.lastIndexOf(' ', end);
                if (ws > i) {
                    end = ws;
                }
            }
            String piece = paragraph.substring(i, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            i = end;
        }
        return pieces;
    }

    private List<ExtractedItem> parseItems(String json) {
        List<ExtractedItem> items = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array != null && array.isArray()) {
                for (JsonNode node : array) {
                    addItem(items, node);
                }
                return items;
            }
        } catch (Exception e) {
            // Not valid JSON as a whole - fall through to salvage.
        }

        // The reply was not a parseable array, most commonly because the model
        // hit its max-tokens budget and the array was cut off mid-object.
        // Recover every complete {...} object we can instead of losing them all.
        List<String> fragments = completeObjects(json);
        if (fragments.isEmpty()) {
            throw new IllegalStateException("Could not parse the LLM extraction reply as JSON: " + json);
        }
        int recovered = 0;
        for (String fragment : fragments) {
            try {
                addItem(items, objectMapper.readTree(fragment));
                recovered++;
            } catch (Exception ignored) {
                // Skip a malformed fragment rather than failing the whole batch.
            }
        }
        log.warn("LLM reply was not a valid JSON array (likely truncated); salvaged {} of {} object fragments",
                recovered, fragments.size());
        return items;
    }

    private void addItem(List<ExtractedItem> items, JsonNode node) {
        String title = node.path("title").asText(null);
        if (title == null || title.isBlank()) {
            return;
        }
        String description = node.path("description").isNull() ? null : node.path("description").asText(null);
        LocalDate plannedDate = parseDateOrNull(node.path("plannedDate").asText(null));
        String module = node.path("module").isNull() ? null : node.path("module").asText(null);
        if (module != null && module.isBlank()) {
            module = null;
        }
        items.add(ExtractedItem.byLlm(title, description, plannedDate, module));
    }

    /**
     * Scans for balanced top-level {@code {...}} objects, respecting string
     * literals and escapes. A trailing object that was never closed (the
     * hallmark of a truncated reply) is simply left out.
     */
    private List<String> completeObjects(String text) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> {
                    if (depth == 0) {
                        start = i;
                    }
                    depth++;
                }
                case '}' -> {
                    if (depth > 0 && --depth == 0 && start >= 0) {
                        objects.add(text.substring(start, i + 1));
                        start = -1;
                    }
                }
                default -> {
                    // ordinary character
                }
            }
        }
        return objects;
    }

    private LocalDate parseDateOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}

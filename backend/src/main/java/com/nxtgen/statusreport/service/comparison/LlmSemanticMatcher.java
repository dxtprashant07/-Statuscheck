package com.nxtgen.statusreport.service.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.statusreport.client.LlmClient;
import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.ItemStatus;
import com.nxtgen.statusreport.model.ProjectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pass 2 of the hybrid comparison pipeline. Only called with the proposal
 * items {@link RuleBasedMatcher} could not confidently align - cases where
 * the status report describes the same work in different words.
 *
 * <p>Proposal items are sent in bounded batches: the model returns one row per
 * proposal item, so a large proposal would otherwise overrun the response token
 * budget and come back truncated. As a backstop, {@link #parseMatches} salvages
 * every complete object from a reply that still arrives truncated.
 */
@Component
public class LlmSemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(LlmSemanticMatcher.class);

    /**
     * Maximum proposal items per LLM call. Bounds the reply size (one row each)
     * and keeps each request within the provider's per-request token limit.
     */
    private static final int BATCH_SIZE = 25;

    private static final String SYSTEM_PROMPT = """
            You align planned project items against a status report and judge
            their completion state, even when the wording differs between the
            two documents. Respond with ONLY a JSON array, no prose, no markdown
            fences. Each element must have exactly these fields:
            {"proposalIndex": number, "statusIndex": number or null,
             "status": "COMPLETED" | "IN_PROGRESS" | "PENDING" | "AT_RISK" | "NOT_STARTED",
             "evidence": short string quoting or paraphrasing the status report}.
            Use statusIndex: null and status: "NOT_STARTED" when the status report
            never mentions a proposal item at all. Include exactly one element per
            proposal item, in the same order they were given.
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmSemanticMatcher(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public List<ItemMatch> match(List<ProjectItem> unmatchedProposalItems, List<ProjectItem> unmatchedStatusItems) {
        if (unmatchedProposalItems.isEmpty()) {
            return List.of();
        }

        List<ItemMatch> results = new ArrayList<>();
        for (int start = 0; start < unmatchedProposalItems.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, unmatchedProposalItems.size());
            // The model indexes proposal items from 0 within each batch; parseMatches
            // maps those local indices back against this sublist.
            List<ProjectItem> batch = unmatchedProposalItems.subList(start, end);
            String reply = llmClient.complete(SYSTEM_PROMPT, buildPrompt(batch, unmatchedStatusItems));
            results.addAll(parseMatches(reply, batch, unmatchedStatusItems));
        }
        return results;
    }

    private String buildPrompt(List<ProjectItem> proposalItems, List<ProjectItem> statusItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("Proposal items:\n");
        for (int i = 0; i < proposalItems.size(); i++) {
            ProjectItem item = proposalItems.get(i);
            sb.append(i).append(". ").append(item.getTitle());
            if (item.getDescription() != null) {
                sb.append(" - ").append(item.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\nStatus report items:\n");
        for (int i = 0; i < statusItems.size(); i++) {
            ProjectItem item = statusItems.get(i);
            sb.append(i).append(". ").append(item.getTitle());
            if (item.getDescription() != null) {
                sb.append(" - ").append(item.getDescription());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<ItemMatch> parseMatches(String json, List<ProjectItem> proposalItems, List<ProjectItem> statusItems) {
        List<ItemMatch> results = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array != null && array.isArray()) {
                for (JsonNode node : array) {
                    addMatch(results, node, proposalItems, statusItems);
                }
                return results;
            }
        } catch (Exception e) {
            // Not parseable as a whole - fall through to salvage.
        }

        // The reply was not a valid array, most often because the model hit its
        // max-tokens budget and the array was cut off. Recover the complete rows.
        List<String> fragments = completeObjects(json);
        if (fragments.isEmpty()) {
            throw new IllegalStateException("Could not parse the LLM comparison reply as JSON: " + json);
        }
        int recovered = 0;
        for (String fragment : fragments) {
            try {
                addMatch(results, objectMapper.readTree(fragment), proposalItems, statusItems);
                recovered++;
            } catch (Exception ignored) {
                // Skip a malformed fragment rather than failing the whole batch.
            }
        }
        log.warn("LLM comparison reply was not a valid JSON array (likely truncated); "
                + "salvaged {} of {} row fragments", recovered, fragments.size());
        return results;
    }

    private void addMatch(List<ItemMatch> results, JsonNode node,
                          List<ProjectItem> proposalItems, List<ProjectItem> statusItems) {
        int proposalIndex = node.path("proposalIndex").asInt(-1);
        if (proposalIndex < 0 || proposalIndex >= proposalItems.size()) {
            return;
        }
        ProjectItem proposalItem = proposalItems.get(proposalIndex);

        ProjectItem statusItem = null;
        JsonNode statusIndexNode = node.path("statusIndex");
        if (!statusIndexNode.isNull() && statusIndexNode.isInt()) {
            int statusIndex = statusIndexNode.asInt();
            if (statusIndex >= 0 && statusIndex < statusItems.size()) {
                statusItem = statusItems.get(statusIndex);
            }
        }

        ItemStatus status = parseStatus(node.path("status").asText("NOT_STARTED"));
        String evidence = node.path("evidence").asText(null);

        results.add(new ItemMatch(proposalItem, statusItem, status, evidence, ExtractionMethod.LLM));
    }

    /**
     * Scans for balanced top-level {@code {...}} objects, respecting string
     * literals and escapes. A trailing object that was never closed (the
     * hallmark of a truncated reply) is left out.
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

    private ItemStatus parseStatus(String raw) {
        try {
            return ItemStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ItemStatus.NOT_STARTED;
        }
    }
}

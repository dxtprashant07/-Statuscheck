package com.nxtgen.statusreport.service.comparison;

import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.ItemStatus;
import com.nxtgen.statusreport.model.ProjectItem;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pass 1 of the hybrid comparison pipeline. Handles the common case where
 * the status report reuses the same (or very similar) wording as the
 * proposal for a given task - fast, deterministic, no API call. Anything
 * left unmatched after this pass goes to {@link LlmSemanticMatcher}.
 */
@Component
public class RuleBasedMatcher {

    private static final double SIMILARITY_THRESHOLD = 0.88;
    private final JaroWinklerSimilarity similarity = new JaroWinklerSimilarity();

    public MatchOutcome match(List<ProjectItem> proposalItems, List<ProjectItem> statusItems) {
        List<ItemMatch> matches = new ArrayList<>();
        List<ProjectItem> unmatched = new ArrayList<>();
        List<ProjectItem> remainingStatusItems = new ArrayList<>(statusItems);

        for (ProjectItem proposalItem : proposalItems) {
            ProjectItem best = findBestMatch(proposalItem, remainingStatusItems);
            if (best != null) {
                remainingStatusItems.remove(best);
                ItemStatus status = classify(best);
                String evidence = best.getDescription() != null ? best.getDescription() : best.getTitle();
                matches.add(new ItemMatch(proposalItem, best, status, evidence, ExtractionMethod.RULE));
            } else {
                unmatched.add(proposalItem);
            }
        }

        return new MatchOutcome(matches, unmatched, remainingStatusItems);
    }

    private ProjectItem findBestMatch(ProjectItem proposalItem, List<ProjectItem> candidates) {
        String normalizedProposalTitle = normalize(proposalItem.getTitle());
        ProjectItem best = null;
        double bestScore = SIMILARITY_THRESHOLD;

        for (ProjectItem candidate : candidates) {
            double score = similarity.apply(normalizedProposalTitle, normalize(candidate.getTitle()));
            if (score >= bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
    }

    /** Keyword heuristic - good enough for explicitly-worded status lines. */
    private ItemStatus classify(ProjectItem statusItem) {
        String text = ((statusItem.getTitle() != null ? statusItem.getTitle() : "") + " "
                + (statusItem.getDescription() != null ? statusItem.getDescription() : "")).toLowerCase(Locale.ROOT);

        if (containsAny(text, "completed", "done", "delivered", "closed", "finished")) {
            return ItemStatus.COMPLETED;
        }
        if (containsAny(text, "risk", "delayed", "blocked", "blocker", "overdue", "stuck")) {
            return ItemStatus.AT_RISK;
        }
        if (containsAny(text, "not started", "yet to start", "not begun")) {
            return ItemStatus.NOT_STARTED;
        }
        if (containsAny(text, "pending", "awaiting", "on hold")) {
            return ItemStatus.PENDING;
        }
        // Mentioned in the status report with no clear completion keyword -
        // assume work has begun rather than guessing COMPLETED.
        return ItemStatus.IN_PROGRESS;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

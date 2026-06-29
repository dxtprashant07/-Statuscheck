package com.nxtgen.statusreport.service.comparison;

import com.nxtgen.statusreport.model.*;
import com.nxtgen.statusreport.repository.ComparisonResultRepository;
import com.nxtgen.statusreport.repository.ProjectItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ComparisonOrchestrator {

    private final RuleBasedMatcher ruleBasedMatcher;
    private final LlmSemanticMatcher llmSemanticMatcher;
    private final ProjectItemRepository projectItemRepository;
    private final ComparisonResultRepository comparisonResultRepository;

    public ComparisonOrchestrator(RuleBasedMatcher ruleBasedMatcher,
                                   LlmSemanticMatcher llmSemanticMatcher,
                                   ProjectItemRepository projectItemRepository,
                                   ComparisonResultRepository comparisonResultRepository) {
        this.ruleBasedMatcher = ruleBasedMatcher;
        this.llmSemanticMatcher = llmSemanticMatcher;
        this.projectItemRepository = projectItemRepository;
        this.comparisonResultRepository = comparisonResultRepository;
    }

    @Transactional
    public List<ComparisonResult> compareAndSave(Project project) {
        List<ProjectItem> proposalItems =
                projectItemRepository.findByProjectIdAndSource(project.getId(), ItemSource.PROPOSAL);
        List<ProjectItem> statusItems =
                projectItemRepository.findByProjectIdAndSource(project.getId(), ItemSource.STATUS_REPORT);

        if (proposalItems.isEmpty()) {
            throw new IllegalStateException(
                    "No proposal items found for this project - upload and extract the proposal document first.");
        }

        // Re-running a comparison (e.g. after a new status report) replaces
        // the previous verdicts rather than appending to them.
        comparisonResultRepository.deleteByProjectId(project.getId());

        MatchOutcome ruleOutcome = ruleBasedMatcher.match(proposalItems, statusItems);
        List<ItemMatch> llmMatches =
                llmSemanticMatcher.match(ruleOutcome.unmatchedProposalItems(), ruleOutcome.unmatchedStatusItems());

        List<ItemMatch> allMatches = new ArrayList<>(ruleOutcome.matches());
        allMatches.addAll(llmMatches);

        List<ComparisonResult> saved = new ArrayList<>();
        for (ItemMatch match : allMatches) {
            ComparisonResult result = new ComparisonResult(
                    project, match.proposalItem(), match.statusItem(),
                    match.status(), match.evidence(), match.matchedBy());
            saved.add(comparisonResultRepository.save(result));
        }
        return saved;
    }
}

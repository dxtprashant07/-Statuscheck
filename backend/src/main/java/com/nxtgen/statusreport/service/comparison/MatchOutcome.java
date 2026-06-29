package com.nxtgen.statusreport.service.comparison;

import com.nxtgen.statusreport.model.ProjectItem;

import java.util.List;

public record MatchOutcome(
        List<ItemMatch> matches,
        List<ProjectItem> unmatchedProposalItems,
        List<ProjectItem> unmatchedStatusItems
) {
}

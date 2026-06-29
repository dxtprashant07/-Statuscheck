package com.nxtgen.statusreport.dto;

import com.nxtgen.statusreport.model.ComparisonResult;
import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.ItemStatus;

public record ComparisonResultDto(
        Long id,
        String proposalItemTitle,
        String proposalItemDescription,
        String statusItemTitle,
        ItemStatus status,
        String evidence,
        ExtractionMethod matchedBy
) {
    public static ComparisonResultDto from(ComparisonResult r) {
        return new ComparisonResultDto(
                r.getId(),
                r.getProposalItem().getTitle(),
                r.getProposalItem().getDescription(),
                r.getStatusItem() != null ? r.getStatusItem().getTitle() : null,
                r.getStatus(),
                r.getEvidence(),
                r.getMatchedBy()
        );
    }
}

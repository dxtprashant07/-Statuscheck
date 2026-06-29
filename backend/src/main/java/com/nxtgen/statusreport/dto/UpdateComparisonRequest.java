package com.nxtgen.statusreport.dto;

import com.nxtgen.statusreport.model.ItemStatus;

/** Payload for a user-corrected comparison verdict. */
public record UpdateComparisonRequest(
        ItemStatus status,
        String evidence
) {
}

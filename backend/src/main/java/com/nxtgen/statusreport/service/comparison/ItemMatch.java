package com.nxtgen.statusreport.service.comparison;

import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.ItemStatus;
import com.nxtgen.statusreport.model.ProjectItem;

public record ItemMatch(
        ProjectItem proposalItem,
        ProjectItem statusItem,
        ItemStatus status,
        String evidence,
        ExtractionMethod matchedBy
) {
}

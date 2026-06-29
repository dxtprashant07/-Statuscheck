package com.nxtgen.statusreport.dto;

import java.util.List;

public record ProjectItemsResponse(List<ProjectItemDto> proposalItems, List<ProjectItemDto> statusItems) {
}

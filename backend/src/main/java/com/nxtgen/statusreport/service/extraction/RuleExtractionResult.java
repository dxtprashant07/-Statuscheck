package com.nxtgen.statusreport.service.extraction;

import java.util.List;

public record RuleExtractionResult(List<ExtractedItem> items, String leftoverText) {
}

package com.nxtgen.statusreport.service.extraction;

import com.nxtgen.statusreport.model.ExtractionMethod;

import java.time.LocalDate;

public record ExtractedItem(
        String title,
        String description,
        LocalDate plannedDate,
        String module,
        ExtractionMethod extractionMethod
) {
    public static ExtractedItem byRule(String title, String description, LocalDate plannedDate, String module) {
        return new ExtractedItem(title, description, plannedDate, module, ExtractionMethod.RULE);
    }

    public static ExtractedItem byLlm(String title, String description, LocalDate plannedDate, String module) {
        return new ExtractedItem(title, description, plannedDate, module, ExtractionMethod.LLM);
    }
}

package com.nxtgen.statusreport.dto;

import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.ProjectItem;

import java.time.LocalDate;

public record ProjectItemDto(
        Long id,
        String title,
        String description,
        LocalDate plannedDate,
        String module,
        ExtractionMethod extractionMethod
) {
    public static ProjectItemDto from(ProjectItem item) {
        return new ProjectItemDto(
                item.getId(), item.getTitle(), item.getDescription(),
                item.getPlannedDate(), item.getModule(), item.getExtractionMethod());
    }
}

package com.nxtgen.statusreport.dto;

import com.nxtgen.statusreport.model.Project;

import java.time.Instant;

public record ProjectDto(Long id, String name, String description, Instant createdAt) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(p.getId(), p.getName(), p.getDescription(), p.getCreatedAt());
    }
}

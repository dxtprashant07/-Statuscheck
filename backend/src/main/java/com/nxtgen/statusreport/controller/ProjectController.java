package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.dto.CreateProjectRequest;
import com.nxtgen.statusreport.dto.ProjectDto;
import com.nxtgen.statusreport.model.Project;
import com.nxtgen.statusreport.repository.ComparisonResultRepository;
import com.nxtgen.statusreport.repository.ProjectItemRepository;
import com.nxtgen.statusreport.repository.ProjectRepository;
import com.nxtgen.statusreport.repository.ProposalDocumentRepository;
import com.nxtgen.statusreport.repository.StatusReportDocumentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ComparisonResultRepository comparisonResultRepository;
    private final ProjectItemRepository projectItemRepository;
    private final ProposalDocumentRepository proposalDocumentRepository;
    private final StatusReportDocumentRepository statusReportDocumentRepository;

    public ProjectController(ProjectRepository projectRepository,
                             ComparisonResultRepository comparisonResultRepository,
                             ProjectItemRepository projectItemRepository,
                             ProposalDocumentRepository proposalDocumentRepository,
                             StatusReportDocumentRepository statusReportDocumentRepository) {
        this.projectRepository = projectRepository;
        this.comparisonResultRepository = comparisonResultRepository;
        this.projectItemRepository = projectItemRepository;
        this.proposalDocumentRepository = proposalDocumentRepository;
        this.statusReportDocumentRepository = statusReportDocumentRepository;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectRepository.findAll().stream().map(ProjectDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<ProjectDto> create(@Valid @RequestBody CreateProjectRequest request) {
        Project saved = projectRepository.save(new Project(request.name(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectDto.from(saved));
    }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable Long id) {
        return ProjectDto.from(findProjectOrThrow(id));
    }

    /**
     * Deletes a project and everything attached to it. Child rows are removed
     * first to respect foreign keys: comparison results reference project items,
     * which (together with the uploaded documents) reference the project.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Project project = findProjectOrThrow(id);
        comparisonResultRepository.deleteByProjectId(project.getId());
        projectItemRepository.deleteByProjectId(project.getId());
        proposalDocumentRepository.deleteByProjectId(project.getId());
        statusReportDocumentRepository.deleteByProjectId(project.getId());
        projectRepository.delete(project);
        return ResponseEntity.noContent().build();
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No project with id " + id));
    }
}

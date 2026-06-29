package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.dto.ComparisonResultDto;
import com.nxtgen.statusreport.dto.UpdateComparisonRequest;
import com.nxtgen.statusreport.model.ComparisonResult;
import com.nxtgen.statusreport.model.ExtractionMethod;
import com.nxtgen.statusreport.model.Project;
import com.nxtgen.statusreport.repository.ComparisonResultRepository;
import com.nxtgen.statusreport.repository.ProjectRepository;
import com.nxtgen.statusreport.service.comparison.ComparisonOrchestrator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/comparison")
public class ComparisonController {

    private final ProjectRepository projectRepository;
    private final ComparisonResultRepository comparisonResultRepository;
    private final ComparisonOrchestrator comparisonOrchestrator;

    public ComparisonController(ProjectRepository projectRepository,
                                 ComparisonResultRepository comparisonResultRepository,
                                 ComparisonOrchestrator comparisonOrchestrator) {
        this.projectRepository = projectRepository;
        this.comparisonResultRepository = comparisonResultRepository;
        this.comparisonOrchestrator = comparisonOrchestrator;
    }

    @PostMapping("/run")
    public List<ComparisonResultDto> run(@PathVariable Long projectId) {
        Project project = findProjectOrThrow(projectId);
        List<ComparisonResult> results = comparisonOrchestrator.compareAndSave(project);
        return results.stream().map(ComparisonResultDto::from).toList();
    }

    @GetMapping
    public List<ComparisonResultDto> get(@PathVariable Long projectId) {
        return comparisonResultRepository.findByProjectIdOrderByIdAsc(projectId)
                .stream().map(ComparisonResultDto::from).toList();
    }

    /**
     * Lets a user correct a verdict the rule/LLM passes got wrong. The status
     * and/or evidence are overwritten and the result is flagged as MANUAL.
     * Transactional so the lazy proposal/status associations are still loadable
     * when mapping the response DTO (open-in-view is off).
     */
    @PatchMapping("/{resultId}")
    @Transactional
    public ComparisonResultDto update(@PathVariable Long projectId,
                                      @PathVariable Long resultId,
                                      @RequestBody UpdateComparisonRequest request) {
        ComparisonResult result = comparisonResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("No comparison result with id " + resultId));
        if (!result.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException(
                    "Comparison result " + resultId + " does not belong to project " + projectId);
        }
        if (request.status() != null) {
            result.setStatus(request.status());
        }
        result.setEvidence(request.evidence());
        result.setMatchedBy(ExtractionMethod.MANUAL);
        return ComparisonResultDto.from(comparisonResultRepository.save(result));
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No project with id " + id));
    }
}

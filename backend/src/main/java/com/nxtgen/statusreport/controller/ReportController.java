package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.model.ComparisonResult;
import com.nxtgen.statusreport.model.Project;
import com.nxtgen.statusreport.repository.ComparisonResultRepository;
import com.nxtgen.statusreport.repository.ProjectRepository;
import com.nxtgen.statusreport.service.report.PptReportGenerator;
import com.nxtgen.statusreport.service.report.WordReportGenerator;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/report")
public class ReportController {

    private final ProjectRepository projectRepository;
    private final ComparisonResultRepository comparisonResultRepository;
    private final WordReportGenerator wordReportGenerator;
    private final PptReportGenerator pptReportGenerator;

    public ReportController(ProjectRepository projectRepository,
                             ComparisonResultRepository comparisonResultRepository,
                             WordReportGenerator wordReportGenerator,
                             PptReportGenerator pptReportGenerator) {
        this.projectRepository = projectRepository;
        this.comparisonResultRepository = comparisonResultRepository;
        this.wordReportGenerator = wordReportGenerator;
        this.pptReportGenerator = pptReportGenerator;
    }

    @GetMapping(value = "/word", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> word(@PathVariable Long projectId) throws IOException {
        Project project = findProjectOrThrow(projectId);
        List<ComparisonResult> results = comparisonResultRepository.findByProjectIdOrderByIdAsc(projectId);
        byte[] bytes = wordReportGenerator.generate(project, results);
        return fileResponse(bytes, project.getName() + "-status-report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @GetMapping(value = "/ppt", produces = "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    public ResponseEntity<byte[]> ppt(@PathVariable Long projectId) throws IOException {
        Project project = findProjectOrThrow(projectId);
        List<ComparisonResult> results = comparisonResultRepository.findByProjectIdOrderByIdAsc(projectId);
        byte[] bytes = pptReportGenerator.generate(project, results);
        return fileResponse(bytes, project.getName() + "-status-report.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String filename, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No project with id " + id));
    }
}

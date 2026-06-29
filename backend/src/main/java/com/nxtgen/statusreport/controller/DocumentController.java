package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.dto.DocumentDto;
import com.nxtgen.statusreport.model.*;
import com.nxtgen.statusreport.repository.ProjectRepository;
import com.nxtgen.statusreport.repository.ProposalDocumentRepository;
import com.nxtgen.statusreport.repository.StatusReportDocumentRepository;
import com.nxtgen.statusreport.service.FileStorageService;
import com.nxtgen.statusreport.service.extraction.ExtractionOrchestrator;
import com.nxtgen.statusreport.service.extraction.TextExtractorFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class DocumentController {

    private final ProjectRepository projectRepository;
    private final ProposalDocumentRepository proposalDocumentRepository;
    private final StatusReportDocumentRepository statusReportDocumentRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractorFactory textExtractorFactory;
    private final ExtractionOrchestrator extractionOrchestrator;

    public DocumentController(ProjectRepository projectRepository,
                               ProposalDocumentRepository proposalDocumentRepository,
                               StatusReportDocumentRepository statusReportDocumentRepository,
                               FileStorageService fileStorageService,
                               TextExtractorFactory textExtractorFactory,
                               ExtractionOrchestrator extractionOrchestrator) {
        this.projectRepository = projectRepository;
        this.proposalDocumentRepository = proposalDocumentRepository;
        this.statusReportDocumentRepository = statusReportDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.textExtractorFactory = textExtractorFactory;
        this.extractionOrchestrator = extractionOrchestrator;
    }

    @PostMapping("/proposal")
    public ResponseEntity<DocumentDto> uploadProposal(@PathVariable Long projectId,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        Project project = findProjectOrThrow(projectId);

        String storagePath = fileStorageService.store(file);
        String rawText = textExtractorFactory.extractText(file.getOriginalFilename(), file.getInputStream());

        ProposalDocument document = new ProposalDocument(project, file.getOriginalFilename(), storagePath);
        document.setRawText(rawText);
        document = proposalDocumentRepository.save(document);

        extractionOrchestrator.extractAndSave(project, ItemSource.PROPOSAL, document.getId(), rawText);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentDto(document.getId(), document.getOriginalFilename(), document.getUploadedAt()));
    }

    @PostMapping("/status-reports")
    public ResponseEntity<DocumentDto> uploadStatusReport(@PathVariable Long projectId,
                                                           @RequestParam("file") MultipartFile file) throws IOException {
        Project project = findProjectOrThrow(projectId);

        String storagePath = fileStorageService.store(file);
        String rawText = textExtractorFactory.extractText(file.getOriginalFilename(), file.getInputStream());

        StatusReportDocument document = new StatusReportDocument(project, file.getOriginalFilename(), storagePath);
        document.setRawText(rawText);
        document = statusReportDocumentRepository.save(document);

        extractionOrchestrator.extractAndSave(project, ItemSource.STATUS_REPORT, document.getId(), rawText);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DocumentDto(document.getId(), document.getOriginalFilename(), document.getUploadedAt()));
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No project with id " + id));
    }
}

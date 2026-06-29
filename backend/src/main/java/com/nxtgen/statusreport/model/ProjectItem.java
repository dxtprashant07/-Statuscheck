package com.nxtgen.statusreport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "project_items")
@Getter
@Setter
@NoArgsConstructor
public class ProjectItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    /** Which document this item was extracted from. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemSource source;

    /** Id of the ProposalDocument or StatusReportDocument it came from. */
    @Column(nullable = false)
    private Long sourceDocumentId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate plannedDate;

    /** Functional area / component this item belongs to (e.g. "Authentication").
     * Null when the extractor couldn't infer one; reports group it under "General". */
    private String module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractionMethod extractionMethod;

    public ProjectItem(Project project, ItemSource source, Long sourceDocumentId,
                        String title, String description, LocalDate plannedDate,
                        String module, ExtractionMethod extractionMethod) {
        this.project = project;
        this.source = source;
        this.sourceDocumentId = sourceDocumentId;
        this.title = title;
        this.description = description;
        this.plannedDate = plannedDate;
        this.module = module;
        this.extractionMethod = extractionMethod;
    }
}

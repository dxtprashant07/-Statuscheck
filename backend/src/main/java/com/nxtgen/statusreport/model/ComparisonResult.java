package com.nxtgen.statusreport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "comparison_results")
@Getter
@Setter
@NoArgsConstructor
public class ComparisonResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_item_id")
    private ProjectItem proposalItem;

    /** Null when the status report never mentions this proposal item at all. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_item_id")
    private ProjectItem statusItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    /** Short quote/paraphrase from the status report backing the verdict. */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractionMethod matchedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ComparisonResult(Project project, ProjectItem proposalItem, ProjectItem statusItem,
                             ItemStatus status, String evidence, ExtractionMethod matchedBy) {
        this.project = project;
        this.proposalItem = proposalItem;
        this.statusItem = statusItem;
        this.status = status;
        this.evidence = evidence;
        this.matchedBy = matchedBy;
    }
}

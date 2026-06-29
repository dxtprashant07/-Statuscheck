package com.nxtgen.statusreport.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "proposal_documents")
@Getter
@Setter
@NoArgsConstructor
public class ProposalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String storagePath;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    public ProposalDocument(Project project, String originalFilename, String storagePath) {
        this.project = project;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
    }
}

package com.nxtgen.statusreport.repository;

import com.nxtgen.statusreport.model.ProposalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalDocumentRepository extends JpaRepository<ProposalDocument, Long> {
    List<ProposalDocument> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}

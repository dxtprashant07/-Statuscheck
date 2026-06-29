package com.nxtgen.statusreport.repository;

import com.nxtgen.statusreport.model.StatusReportDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusReportDocumentRepository extends JpaRepository<StatusReportDocument, Long> {
    List<StatusReportDocument> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}

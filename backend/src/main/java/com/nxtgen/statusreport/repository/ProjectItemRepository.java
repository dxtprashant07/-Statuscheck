package com.nxtgen.statusreport.repository;

import com.nxtgen.statusreport.model.ItemSource;
import com.nxtgen.statusreport.model.ProjectItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectItemRepository extends JpaRepository<ProjectItem, Long> {
    List<ProjectItem> findByProjectIdAndSource(Long projectId, ItemSource source);

    List<ProjectItem> findBySourceDocumentIdAndSource(Long sourceDocumentId, ItemSource source);

    void deleteByProjectId(Long projectId);
}

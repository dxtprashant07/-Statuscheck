package com.nxtgen.statusreport.repository;

import com.nxtgen.statusreport.model.ComparisonResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComparisonResultRepository extends JpaRepository<ComparisonResult, Long> {

    /**
     * Eagerly fetches the proposal/status items so callers (the comparison DTO
     * mapping and the Word/PPT report generators) can read them after the
     * transaction closes. Without the JOIN FETCH these LAZY associations throw
     * LazyInitializationException, since spring.jpa.open-in-view is false.
     */
    @Query("select cr from ComparisonResult cr "
            + "left join fetch cr.proposalItem "
            + "left join fetch cr.statusItem "
            + "where cr.project.id = :projectId "
            + "order by cr.id asc")
    List<ComparisonResult> findByProjectIdOrderByIdAsc(@Param("projectId") Long projectId);

    void deleteByProjectId(Long projectId);
}

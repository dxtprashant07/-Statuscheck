package com.nxtgen.statusreport.repository;

import com.nxtgen.statusreport.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}

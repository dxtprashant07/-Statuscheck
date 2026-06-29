package com.nxtgen.statusreport.controller;

import com.nxtgen.statusreport.dto.ProjectItemDto;
import com.nxtgen.statusreport.dto.ProjectItemsResponse;
import com.nxtgen.statusreport.model.ItemSource;
import com.nxtgen.statusreport.repository.ProjectItemRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/items")
public class ItemController {

    private final ProjectItemRepository projectItemRepository;

    public ItemController(ProjectItemRepository projectItemRepository) {
        this.projectItemRepository = projectItemRepository;
    }

    @GetMapping
    public ProjectItemsResponse list(@PathVariable Long projectId) {
        var proposalItems = projectItemRepository.findByProjectIdAndSource(projectId, ItemSource.PROPOSAL)
                .stream().map(ProjectItemDto::from).toList();
        var statusItems = projectItemRepository.findByProjectIdAndSource(projectId, ItemSource.STATUS_REPORT)
                .stream().map(ProjectItemDto::from).toList();
        return new ProjectItemsResponse(proposalItems, statusItems);
    }
}

package com.nxtgen.statusreport.service.extraction;

import com.nxtgen.statusreport.model.ItemSource;
import com.nxtgen.statusreport.model.Project;
import com.nxtgen.statusreport.model.ProjectItem;
import com.nxtgen.statusreport.repository.ProjectItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExtractionOrchestrator {

    private final RuleBasedExtractor ruleBasedExtractor;
    private final LlmExtractor llmExtractor;
    private final ProjectItemRepository projectItemRepository;

    public ExtractionOrchestrator(RuleBasedExtractor ruleBasedExtractor,
                                   LlmExtractor llmExtractor,
                                   ProjectItemRepository projectItemRepository) {
        this.ruleBasedExtractor = ruleBasedExtractor;
        this.llmExtractor = llmExtractor;
        this.projectItemRepository = projectItemRepository;
    }

    /**
     * Runs the rule pass first; only the text it couldn't parse is sent to the
     * LLM pass. Both outputs are merged, saved as {@link ProjectItem}s and
     * returned.
     */
    public List<ProjectItem> extractAndSave(Project project, ItemSource source,
                                              Long sourceDocumentId, String rawText) {
        RuleExtractionResult ruleResult = ruleBasedExtractor.extract(rawText);
        List<ExtractedItem> llmItems = llmExtractor.extract(ruleResult.leftoverText());

        List<ExtractedItem> all = new ArrayList<>(ruleResult.items());
        all.addAll(llmItems);

        List<ProjectItem> saved = new ArrayList<>();
        for (ExtractedItem item : all) {
            ProjectItem entity = new ProjectItem(
                    project, source, sourceDocumentId,
                    item.title(), item.description(), item.plannedDate(),
                    item.module(), item.extractionMethod());
            saved.add(projectItemRepository.save(entity));
        }
        return saved;
    }
}

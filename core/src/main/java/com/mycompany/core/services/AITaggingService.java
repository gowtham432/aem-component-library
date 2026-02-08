package com.mycompany.core.services;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.List;

public interface AITaggingService {

    /**
     * Analyze page and generate tag suggestions
     * @param page The AEM page to analyze
     * @return List of tag IDs to apply
     */
    List<String> analyzeAndGenerateTags(Page page);

    /**
     * Apply tags to a page
     * @param page The page to tag
     * @param tagIds List of tag IDs
     */
    void applyTagsToPage(Page page, List<String> tagIds);

    /**
     * Apply tags to a resource (Content Fragment, Experience Fragment, or Asset)
     * @param resource The resource to tag
     * @param tagIds List of tag IDs
     * @param resolver Resource resolver for committing changes
     */
    void applyTagsToResource(Resource resource, List<String> tagIds, ResourceResolver resolver);

    /**
     * Map AI concepts to AEM tag IDs (for backward compatibility)
     * @param concepts List of concept strings
     * @return List of tag IDs
     */
    List<String> mapConceptsToTags(List<String> concepts);
}

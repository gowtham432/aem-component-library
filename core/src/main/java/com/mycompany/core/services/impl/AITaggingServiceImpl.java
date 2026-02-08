package com.mycompany.core.services.impl;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.day.cq.wcm.api.Page;
import com.mycompany.core.services.AITaggingService;
import com.mycompany.core.services.OpenAIService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component(service = AITaggingService.class, immediate = true)
public class AITaggingServiceImpl implements AITaggingService {

    private static final Logger LOG = LoggerFactory.getLogger(AITaggingServiceImpl.class);

    @Reference
    private OpenAIService openAIService;

    // Mapping of AI concepts to AEM tag IDs (for backward compatibility)
    private static final Map<String, String> CONCEPT_TAG_MAP = Map.ofEntries(
            Map.entry("article", "myaemproject:content-type/article"),
            Map.entry("product-launch", "myaemproject:content-type/product-launch"),
            Map.entry("blog-post", "myaemproject:content-type/blog-post"),
            Map.entry("tutorial", "myaemproject:content-type/tutorial"),
            Map.entry("automotive", "myaemproject:topic/automotive"),
            Map.entry("electric-vehicles", "myaemproject:topic/automotive/electric-vehicles"),
            Map.entry("suv", "myaemproject:topic/automotive/suv"),
            Map.entry("sustainability", "myaemproject:topic/sustainability"),
            Map.entry("clean-energy", "myaemproject:topic/sustainability/clean-energy"),
            Map.entry("eco-friendly", "myaemproject:topic/sustainability/eco-friendly"),
            Map.entry("families", "myaemproject:audience/families"),
            Map.entry("tech-enthusiasts", "myaemproject:audience/tech-enthusiasts"),
            Map.entry("professionals", "myaemproject:audience/professionals"),
            Map.entry("autopilot", "myaemproject:feature/autopilot"),
            Map.entry("long-range", "myaemproject:feature/long-range-battery"),
            Map.entry("fast-charging", "myaemproject:feature/fast-charging"),
            Map.entry("education", "myaemproject:intent/education"),
            Map.entry("conversion", "myaemproject:intent/conversion"),
            Map.entry("awareness", "myaemproject:intent/brand-awareness")
    );

    @Override
    public List<String> analyzeAndGenerateTags(Page page) {
        // This method is now mostly handled by the listener
        // Keeping for backward compatibility
        String pageContent = extractPageContent(page);

        // Note: This method signature doesn't include availableTags
        // For new implementations, use the listener which calls OpenAI directly
        LOG.debug("analyzeAndGenerateTags called for page: {}", page.getPath());

        return new ArrayList<>();
    }

    @Override
    public void applyTagsToPage(Page page, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            LOG.warn("No tags to apply to page: {}", page.getPath());
            return;
        }

        ResourceResolver resolver = page.getContentResource().getResourceResolver();
        TagManager tagManager = resolver.adaptTo(TagManager.class);

        if (tagManager == null) {
            LOG.error("Could not get TagManager");
            return;
        }

        try {
            // Convert tag IDs to Tag objects
            Tag[] tags = tagIds.stream()
                    .map(tagManager::resolve)
                    .filter(Objects::nonNull)
                    .toArray(Tag[]::new);

            if (tags.length == 0) {
                LOG.warn("No valid tags found to apply to page: {}", page.getPath());
                return;
            }

            // Apply tags
            tagManager.setTags(page.getContentResource(), tags);

            LOG.info("Applied {} tags to page: {}", tags.length, page.getPath());

        } catch (Exception e) {
            LOG.error("Error applying tags to page: " + page.getPath(), e);
        }
    }

    @Override
    public void applyTagsToResource(Resource resource, List<String> tagIds, ResourceResolver resolver) {
        if (tagIds == null || tagIds.isEmpty()) {
            LOG.warn("No tags to apply to resource: {}", resource.getPath());
            return;
        }

        TagManager tagManager = resolver.adaptTo(TagManager.class);

        if (tagManager == null) {
            LOG.error("Could not get TagManager");
            return;
        }

        try {
            // For Content Fragments and Experience Fragments, tags might be on jcr:content
            Resource targetResource = resource;
            Resource jcrContent = resource.getChild("jcr:content");

            if (jcrContent != null) {
                targetResource = jcrContent;
            }

            // Convert tag IDs to Tag objects
            Tag[] tags = tagIds.stream()
                    .map(tagManager::resolve)
                    .filter(Objects::nonNull)
                    .toArray(Tag[]::new);

            if (tags.length == 0) {
                LOG.warn("No valid tags found to apply to resource: {}", resource.getPath());
                return;
            }

            // Apply tags
            tagManager.setTags(targetResource, tags);

            LOG.info("Applied {} tags to resource: {}", tags.length, resource.getPath());

        } catch (Exception e) {
            LOG.error("Error applying tags to resource: " + resource.getPath(), e);
        }
    }

    @Override
    public List<String> mapConceptsToTags(List<String> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return new ArrayList<>();
        }

        // Since OpenAI now returns tag IDs directly, this is mostly a pass-through
        // But we keep it for validation and backward compatibility
        return concepts.stream()
                .map(concept -> {
                    String normalized = concept.toLowerCase().trim();

                    // If it looks like a tag ID already (contains colon), return as-is
                    if (normalized.contains(":")) {
                        return concept.trim();
                    }

                    // Otherwise try to map it
                    String tagId = CONCEPT_TAG_MAP.get(normalized);
                    if (tagId != null) {
                        return tagId;
                    }

                    // Try fuzzy matching
                    for (Map.Entry<String, String> entry : CONCEPT_TAG_MAP.entrySet()) {
                        if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                            return entry.getValue();
                        }
                    }

                    LOG.debug("Could not map concept to tag: {}", concept);
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Extract all text content from the page
     */
    private String extractPageContent(Page page) {
        StringBuilder content = new StringBuilder();

        // Add page title
        content.append("Title: ").append(page.getTitle()).append("\n");

        // Add page description
        if (page.getDescription() != null) {
            content.append("Description: ").append(page.getDescription()).append("\n");
        }

        // Add page properties that might be relevant
        content.append("Page Name: ").append(page.getName()).append("\n");

        return content.toString();
    }
}

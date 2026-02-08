package com.mycompany.core.services.impl;

import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;
import com.mycompany.core.services.TagProviderService;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = TagProviderService.class, immediate = true)
public class TagProviderServiceImpl implements TagProviderService {

    private static final Logger LOG = LoggerFactory.getLogger(TagProviderServiceImpl.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    // Root namespace for your tags
    private static final String TAGS_ROOT_PATH = "/content/cq:tags";

    @Override
    public Map<String, String> getAllAvailableTags(ResourceResolver resolver) {
        Map<String, String> tagMap = new LinkedHashMap<>();


        TagManager tagManager = resolver.adaptTo(TagManager.class);
        if (tagManager == null) {
            LOG.error("Could not get TagManager");
            return tagMap;
        }

        // Get root namespace tag
        Tag namespaceTag = tagManager.resolve(TAGS_ROOT_PATH);

        if (namespaceTag == null) {
            LOG.warn("Tag namespace not found: {}", TAGS_ROOT_PATH);
            return tagMap;
        }

        // Recursively collect all tags
        collectTagsRecursive(namespaceTag, tagMap);

        LOG.debug("Found {} tags in namespace {}", tagMap.size(), TAGS_ROOT_PATH);

        return tagMap;
    }

//    @Override
//    public String getFormattedTagsForAI() {
//        Map<String, String> tags = getAllAvailableTags();
//
//        if (tags.isEmpty()) {
//            return "No tags available";
//        }
//
//        StringBuilder formatted = new StringBuilder();
//        formatted.append("Available tags (return ONLY tag IDs from this list):\n\n");
//
//        // Group by category
//        Map<String, List<Map.Entry<String, String>>> categorized = new LinkedHashMap<>();
//
//        for (Map.Entry<String, String> entry : tags.entrySet()) {
//            String tagId = entry.getKey();
//            String category = extractCategory(tagId);
//
//            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
//        }
//
//        // Format by category
//        for (Map.Entry<String, List<Map.Entry<String, String>>> categoryEntry : categorized.entrySet()) {
//            String category = categoryEntry.getKey();
//            List<Map.Entry<String, String>> categoryTags = categoryEntry.getValue();
//
//            formatted.append(category.toUpperCase()).append(":\n");
//
//            for (Map.Entry<String, String> tag : categoryTags) {
//                formatted.append("  - ").append(tag.getKey())
//                        .append(" (").append(tag.getValue()).append(")\n");
//            }
//
//            formatted.append("\n");
//        }
//
//        return formatted.toString();
//    }

    /**
     * Recursively collect all tags
     */
    private void collectTagsRecursive(Tag tag, Map<String, String> tagMap) {
        if (tag == null) {
            return;
        }

        // Add current tag
        tagMap.put(tag.getTagID(), tag.getTitle());

        // Process children
        Iterator<Tag> children = tag.listChildren();
        while (children.hasNext()) {
            collectTagsRecursive(children.next(), tagMap);
        }
    }

    /**
     * Extract category from tag ID
     */
    private String extractCategory(String tagId) {
        // Remove namespace
        String withoutNamespace = tagId.replace(TAGS_ROOT_PATH + ":", "");

        // Get first segment
        int slashIndex = withoutNamespace.indexOf('/');
        if (slashIndex > 0) {
            return withoutNamespace.substring(0, slashIndex);
        }

        return withoutNamespace;
    }
}

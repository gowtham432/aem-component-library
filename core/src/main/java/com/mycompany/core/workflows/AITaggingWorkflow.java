package com.mycompany.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.mycompany.core.services.AITaggingService;
import com.mycompany.core.services.OpenAIService;
import com.mycompany.core.services.TagProviderService;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=AI Tagging Workflow Process"
        }
)
public class AITaggingWorkflow implements WorkflowProcess {

    @Reference
    private TagProviderService tagProviderService;

    @Reference
    private OpenAIService openAIService;

    @Reference
    private AITaggingService aiTaggingService;

    // Properties to EXCLUDE (system/metadata properties not relevant for tagging)
    private static final Set<String> EXCLUDED_PROPERTIES = Set.of(
            "jcr:created",
            "jcr:createdBy",
            "jcr:lastModified",
            "jcr:lastModifiedBy",
            "cq:lastModified",
            "cq:lastModifiedBy",
            "cq:lastReplicated",
            "cq:lastReplicatedBy",
            "cq:lastReplicationAction",
            "cq:lastRolledout",
            "cq:lastRolledoutBy",
            "jcr:uuid",
            "jcr:baseVersion",
            "jcr:predecessors",
            "jcr:versionHistory",
            "jcr:isCheckedOut",
            "cq:lastPublished",
            "cq:lastPublishedBy"
    );

    private static final Set<String> TEXT_PROPERTIES = Set.of(
            "text", "jcr:title", "title", "jcr:description",
            "description", "alt", "heading", "subtitle",
            "caption", "label", "value", "content", "name"
    );

    private static final int MAX_DEPTH = 10;
    private static final Logger LOG = LoggerFactory.getLogger(AITaggingWorkflow.class);

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap)
            throws WorkflowException {

        String payloadPath = workItem.getWorkflowData().getPayload().toString();
        LOG.info("Processing AI Tagging Workflow for payload: {}", payloadPath);

        try (ResourceResolver resolver = getWorkflowResolver(workflowSession)) {

            Resource resource = resolver.getResource(payloadPath);

            if (resource == null) {
                LOG.error("❌ Resource not found: {}", payloadPath);
                return;
            }

            LOG.info("✅ Resource found: {}", payloadPath);

            if (isPage(payloadPath)) {
                processPage(resource, resolver);
            }

        } catch (Exception e) {
            LOG.error("❌ Error in AI tagging workflow for: " + payloadPath, e);
        }

        LOG.info("AI Tagging Workflow completed successfully for: {}", payloadPath);
    }

    private ResourceResolver getWorkflowResolver(WorkflowSession workflowSession) throws LoginException {
        return workflowSession.adaptTo(ResourceResolver.class);
    }

    private boolean isPage(String path) {
        return path.startsWith("/content/") &&
                !path.startsWith("/content/dam") &&
                !path.startsWith("/content/experience-fragments");
    }

    private boolean processPage(Resource pageResource, ResourceResolver resolver) {
        try {
            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager == null) {
                LOG.error("❌ Could not get PageManager");
                return false;
            }

            Page page = pageManager.getPage(pageResource.getPath());
            if (page == null) {
                LOG.warn("⚠️ Could not adapt resource to Page: {}", pageResource.getPath());
                return false;
            }

            LOG.info("📄 Processing page: {}", page.getPath());

            // Build clean JSON structure by excluding system properties
            Map<String, Object> cleanJson = buildCleanJsonTree(pageResource, 0);

            // Extract text content for AI
            String textContent = extractTextFromJson(cleanJson);

            if (textContent == null || textContent.trim().isEmpty()) {
                LOG.warn("⚠️ No text content extracted from page: {}", page.getPath());
                return false;
            }

            LOG.info("📝 Extracted {} characters of text", textContent.length());

            // Build full content for AI
            String fullContent = buildPageContent(page, textContent);
            LOG.info("🤖 Content for AI:\n{}", fullContent);

            // TODO: Call your AI service here with cleanJson or fullContent
             Map<String, String> availableTags = tagProviderService.getAllAvailableTags(resolver);
             List<String> suggestedTagIds = openAIService.generateTagSuggestions(fullContent, availableTags);
             aiTaggingService.applyTagsToPage(page, suggestedTagIds);
            // resolver.commit();

            return true;

        } catch (Exception e) {
            LOG.error("❌ Error processing page", e);
            return false;
        }
    }

    /**
     * Build JSON tree excluding system/metadata properties
     */
    private Map<String, Object> buildCleanJsonTree(Resource resource, int depth) {
        Map<String, Object> node = new LinkedHashMap<>();

        if (resource == null || depth > MAX_DEPTH) {
            return node;
        }

        // Add all properties EXCEPT excluded ones
        ValueMap properties = resource.getValueMap();
        properties.forEach((key, value) -> {
            if (!EXCLUDED_PROPERTIES.contains(key)) {
                node.put(key, value);
            }
        });

        // Recursively process children
        for (Resource child : resource.getChildren()) {
            Map<String, Object> childNode = buildCleanJsonTree(child, depth + 1);
            if (!childNode.isEmpty()) {
                node.put(child.getName(), childNode);
            }
        }

        return node;
    }

    /**
     * Extract text content from clean JSON for AI processing
     */
    private String extractTextFromJson(Map<String, Object> jsonMap) {
        StringBuilder text = new StringBuilder();
        extractTextRecursive(jsonMap, text);
        return text.toString().trim();
    }

    private void extractTextRecursive(Map<String, Object> map, StringBuilder text) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Extract text from known text properties
            if (TEXT_PROPERTIES.contains(key) && value instanceof String) {
                String textValue = ((String) value).trim();
                if (!textValue.isEmpty()) {
                    text.append(textValue).append(" ");
                }
            }

            // Recurse into nested maps
            if (value instanceof Map) {
                extractTextRecursive((Map<String, Object>) value, text);
            }
        }
    }

    private String buildPageContent(Page page, String extractedText) {
        StringBuilder content = new StringBuilder();
        content.append("Page Title: ").append(page.getTitle() != null ? page.getTitle() : "").append("\n");
        content.append("Page Name: ").append(page.getName()).append("\n");

        if (page.getDescription() != null && !page.getDescription().isEmpty()) {
            content.append("Description: ").append(page.getDescription()).append("\n");
        }

        content.append("\nPage Content:\n").append(extractedText);
        return content.toString();
    }
}

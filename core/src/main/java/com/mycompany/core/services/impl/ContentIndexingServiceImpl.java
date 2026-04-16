package com.mycompany.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.mycompany.core.models.PageDocument;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.ContentIndexingService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component(service = ContentIndexingService.class, immediate = true)
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentIndexingServiceImpl.class);
    private static final String SERVICE_USER = "content-intelligence-service";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Reference
    private AIConfigService config;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public List<PageDocument> indexAllPages() {
        Map<String, Object> authInfo = Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            return crawlPages(resolver, config.getContentRootPath());
        } catch (Exception e) {
            LOG.error("Failed to index pages under {}: {}", config.getContentRootPath(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public PageDocument indexPage(String pagePath) {
        Map<String, Object> authInfo = Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            Resource pageResource = resolver.getResource(pagePath);
            if (pageResource == null) {
                LOG.warn("Page resource not found: {}", pagePath);
                return null;
            }
            Page page = pageResource.adaptTo(Page.class);
            if (page == null) {
                LOG.warn("Resource is not a cq:Page: {}", pagePath);
                return null;
            }
            return buildPageDocument(page, resolver);
        } catch (Exception e) {
            LOG.error("Failed to index page {}: {}", pagePath, e.getMessage(), e);
            return null;
        }
    }

    private List<PageDocument> crawlPages(ResourceResolver resolver, String rootPath) {
        List<PageDocument> docs = new ArrayList<>();
        Resource rootResource = resolver.getResource(rootPath);
        if (rootResource == null) {
            LOG.warn("Root content path not found: {}", rootPath);
            return docs;
        }

        PageManager pageManager = resolver.adaptTo(PageManager.class);
        if (pageManager == null) {
            return docs;
        }

        Page rootPage = pageManager.getPage(rootPath);
        if (rootPage == null) {
            return docs;
        }

        Iterator<Page> children = rootPage.listChildren();
        while (children.hasNext()) {
            collectPages(children.next(), docs, resolver);
        }
        LOG.info("Indexed {} pages under {}", docs.size(), rootPath);
        return docs;
    }

    private void collectPages(Page page, List<PageDocument> docs, ResourceResolver resolver) {
        PageDocument doc = buildPageDocument(page, resolver);
        if (doc != null) {
            docs.add(doc);
        }

        Iterator<Page> children = page.listChildren();
        while (children.hasNext()) {
            collectPages(children.next(), docs, resolver);
        }
    }

    private PageDocument buildPageDocument(Page page, ResourceResolver resolver) {
        try {
            PageDocument doc = new PageDocument();
            doc.setPagePath(page.getPath());
            doc.setTitle(page.getTitle() != null ? page.getTitle() : page.getName());
            doc.setDescription(page.getDescription());

            // Tags
            String[] tagIds = page.getProperties().get("cq:tags", String[].class);
            if (tagIds != null) {
                List<String> tagList = new ArrayList<>();
                for (String tagId : tagIds) {
                    tagList.add(simplifyTagId(tagId));
                }
                doc.setTags(tagList);
            } else {
                doc.setTags(new ArrayList<>());
            }

            // Template
            doc.setTemplate(page.getProperties().get("cq:template", String.class));

            // Dates
            Calendar publishedCal = page.getProperties().get("cq:lastPublished", Calendar.class);
            if (publishedCal != null) {
                doc.setPublishedDate(DATE_FORMAT.format(publishedCal.getTime()));
            }
            Calendar modifiedCal = page.getLastModified();
            if (modifiedCal != null) {
                doc.setLastModified(DATE_FORMAT.format(modifiedCal.getTime()));
            }

            // Category derived from path segment after root
            doc.setCategory(extractCategory(page.getPath()));

            // Components — walk the jcr:content/root tree
            extractComponentData(page, doc, resolver);

            return doc;
        } catch (Exception e) {
            LOG.warn("Error building document for page {}: {}", page.getPath(), e.getMessage());
            return null;
        }
    }

    private void extractComponentData(Page page, PageDocument doc, ResourceResolver resolver) {
        Resource content = page.getContentResource();
        if (content == null) {
            doc.setComponents(new ArrayList<>());
            return;
        }

        List<String> componentTypes = new ArrayList<>();
        Map<String, String> componentData = new HashMap<>();

        // Walk root/responsivegrid (or just root) for components
        Resource root = content.getChild("root");
        if (root == null) {
            root = content;
        }

        walkComponentTree(root, componentTypes, componentData);

        doc.setComponents(componentTypes);
        doc.setComponentCount(componentTypes.size());

        boolean hasHero = componentTypes.contains("hero") || componentTypes.contains("responsivegrid/hero");
        doc.setHasHero(hasHero);
        doc.setHasVideo(componentTypes.stream().anyMatch(c -> c.contains("video") || c.contains("embed")));
        doc.setHasCta(componentTypes.contains("button") || componentData.containsKey("heroCTAText"));

        if (hasHero && componentData.containsKey("heroTitle")) {
            doc.setHeroTitle(componentData.get("heroTitle"));
        }
        if (componentData.containsKey("heroCTAText")) {
            doc.setHeroCTAText(componentData.get("heroCTAText"));
        }
    }

    private void walkComponentTree(Resource resource, List<String> types, Map<String, String> data) {
        try {
            Node node = resource.adaptTo(Node.class);
            if (node == null) return;

            String resourceType = resource.getResourceType();
            if (resourceType != null && !resourceType.isEmpty()) {
                String shortType = resourceType.contains("/")
                        ? resourceType.substring(resourceType.lastIndexOf('/') + 1)
                        : resourceType;

                if (!shortType.equals("responsivegrid") && !shortType.equals("container")
                        && !shortType.equals("parsys") && !shortType.isEmpty()) {
                    types.add(shortType);

                    // Hero-specific extraction
                    if (shortType.equals("hero") || shortType.contains("hero")) {
                        extractHeroData(resource, data);
                    }
                    // Button / CTA
                    if (shortType.equals("button")) {
                        String btnText = resource.getValueMap().get("text", String.class);
                        if (btnText != null && !btnText.isEmpty()) {
                            data.put("heroCTAText", btnText);
                        }
                    }
                }
            }

            // Recurse into children
            NodeIterator children = node.getNodes();
            while (children.hasNext()) {
                Node childNode = children.nextNode();
                Resource childResource = resource.getChild(childNode.getName());
                if (childResource != null) {
                    walkComponentTree(childResource, types, data);
                }
            }
        } catch (RepositoryException e) {
            LOG.debug("Error walking component tree: {}", e.getMessage());
        }
    }

    private void extractHeroData(Resource heroResource, Map<String, String> data) {
        String title = heroResource.getValueMap().get("jcr:title", String.class);
        if (title == null) title = heroResource.getValueMap().get("title", String.class);
        if (title != null && !title.isEmpty()) {
            data.put("heroTitle", title);
        }

        String ctaText = heroResource.getValueMap().get("ctaText", String.class);
        if (ctaText == null) ctaText = heroResource.getValueMap().get("actionText", String.class);
        if (ctaText != null && !ctaText.isEmpty()) {
            data.put("heroCTAText", ctaText);
        }
    }

    private String extractCategory(String pagePath) {
        // e.g. /content/myaemproject/us/en/adventures/bali-surf-camp -> "adventures"
        // segments: [0]="" [1]="content" [2]="myaemproject" [3]="us" [4]="en" [5]="adventures" [6]="bali-surf-camp"
        String root = config.getContentRootPath(); // e.g. /content/myaemproject/us/en
        String relative = pagePath.startsWith(root) ? pagePath.substring(root.length()) : pagePath;
        // relative = /adventures/bali-surf-camp
        String[] parts = relative.split("/");
        // parts[0]="" [1]="adventures" [2]="bali-surf-camp"
        if (parts.length > 1 && !parts[1].isEmpty()) {
            return parts[1];
        }
        return "general";
    }

    private String simplifyTagId(String tagId) {
        // e.g. "wknd:activity/surfing" -> "surfing"
        if (tagId.contains("/")) {
            return tagId.substring(tagId.lastIndexOf('/') + 1);
        }
        if (tagId.contains(":")) {
            return tagId.substring(tagId.indexOf(':') + 1);
        }
        return tagId;
    }
}

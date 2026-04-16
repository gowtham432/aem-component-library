package com.mycompany.core.servlets;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mycompany.core.services.AIConfigService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * GET /bin/api/pageList
 *
 * Returns all pages under the configured content root for the page selector dropdown.
 *
 * Response:
 * { "pages": [{ "path": "/content/wknd/...", "title": "Page Title" }, ...] }
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/api/pageList",
        "sling.servlet.methods=GET",
        "sling.auth.requirements=-/bin/api/pageList"
})
public class PageListServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PageListServlet.class);
    private static final Gson GSON = new Gson();
    private static final String SERVICE_USER = "content-intelligence-service";

    @Reference
    private AIConfigService config;

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        String rootPath = config.getContentRootPath();
        Map<String, Object> authInfo = Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE, SERVICE_USER);

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(authInfo)) {
            JsonArray pages = new JsonArray();
            JsonObject result = new JsonObject();

            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager != null) {
                Page rootPage = pageManager.getPage(rootPath);
                if (rootPage != null) {
                    Iterator<Page> children = rootPage.listChildren();
                    while (children.hasNext()) {
                        collectPageEntries(children.next(), pages);
                    }
                }
            }

            result.add("pages", pages);
            response.setStatus(200);
            response.getWriter().write(GSON.toJson(result));

        } catch (Exception e) {
            LOG.error("PageListServlet error: {}", e.getMessage(), e);
            response.setStatus(500);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Failed to retrieve page list: " + e.getMessage());
            response.getWriter().write(err.toString());
        }
    }

    private void collectPageEntries(Page page, JsonArray pages) {
        // Add current page
        JsonObject entry = new JsonObject();
        entry.addProperty("path", page.getPath());
        String title = page.getTitle();
        entry.addProperty("title", title != null ? title : page.getName());
        pages.add(entry);

        // Recurse into children
        Iterator<Page> children = page.listChildren();
        while (children.hasNext()) {
            collectPageEntries(children.next(), pages);
        }
    }
}

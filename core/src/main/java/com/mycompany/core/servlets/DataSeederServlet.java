package com.mycompany.core.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mycompany.core.models.PageDocument;
import com.mycompany.core.services.ContentIndexingService;
import com.mycompany.core.services.MockAnalyticsService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.utils.HttpClientUtil;
import com.mycompany.core.services.AIConfigService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /bin/api/seedData
 *
 * One-time setup: crawls all WKND pages, generates mock analytics,
 * creates embeddings, and upserts into Pinecone.
 *
 * Response: JSON progress log listing each indexed page.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/api/seedData",
        "sling.servlet.methods=POST",
        "sling.auth.requirements=-/bin/api/seedData"
})
public class DataSeederServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DataSeederServlet.class);
    private static final Gson GSON = new Gson();
    private static final String OPENAI_EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";

    @Reference
    private AIConfigService config;

    @Reference
    private ContentIndexingService contentIndexingService;

    @Reference
    private MockAnalyticsService mockAnalyticsService;

    @Reference
    private PineconeService pineconeService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        List<String> log = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try {
            log.add("Starting data seeding...");

            // Step 1: Crawl pages
            log.add("Crawling pages under: " + config.getContentRootPath());
            List<PageDocument> pages = contentIndexingService.indexAllPages();
            log.add("Found " + pages.size() + " pages.");

            // Step 2–5: Enrich, embed, upsert each page
            for (PageDocument doc : pages) {
                try {
                    // Enrich with mock analytics
                    mockAnalyticsService.enrichWithAnalytics(doc);

                    // Persist analytics to JCR
                    mockAnalyticsService.saveToJcr(doc);

                    // Generate embedding
                    String embeddingText = doc.toEmbeddingText();
                    List<Float> embedding = getEmbedding(embeddingText);

                    if (embedding.isEmpty()) {
                        log.add("SKIP (no embedding): " + doc.getPagePath());
                        failCount++;
                        continue;
                    }

                    // Build Pinecone metadata
                    Map<String, Object> metadata = buildMetadata(doc);

                    // Upsert into Pinecone
                    String vectorId = sanitizeId(doc.getPagePath());
                    boolean ok = pineconeService.upsertDocument(vectorId, embedding, metadata);

                    if (ok) {
                        log.add("OK: " + doc.getPagePath() + " [" + doc.getArchetype() + ", views=" + doc.getPageViews() + "]");
                        successCount++;
                    } else {
                        log.add("FAIL (upsert): " + doc.getPagePath());
                        failCount++;
                    }

                    // Brief pause to respect OpenAI rate limits
                    Thread.sleep(200);

                } catch (Exception e) {
                    log.add("ERROR: " + doc.getPagePath() + " — " + e.getMessage());
                    LOG.error("Error seeding page {}: {}", doc.getPagePath(), e.getMessage(), e);
                    failCount++;
                }
            }

            log.add("Seeding complete. Success: " + successCount + ", Failed: " + failCount);

            JsonObject result = new JsonObject();
            result.addProperty("success", successCount);
            result.addProperty("failed", failCount);
            result.addProperty("total", pages.size());
            JsonArray logArray = new JsonArray();
            log.forEach(logArray::add);
            result.add("log", logArray);

            response.setStatus(200);
            response.getWriter().write(GSON.toJson(result));

        } catch (Exception e) {
            LOG.error("Data seeding failed: {}", e.getMessage(), e);
            JsonObject err = new JsonObject();
            err.addProperty("error", "Seeding failed: " + e.getMessage());
            JsonArray logArray = new JsonArray();
            log.forEach(logArray::add);
            err.add("log", logArray);
            response.setStatus(500);
            response.getWriter().write(err.toString());
        }
    }

    private List<Float> getEmbedding(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getOpenAiEmbeddingModel());
            body.put("input", text);
            body.put("dimensions", 1024);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getOpenAiApiKey());

            String responseStr = HttpClientUtil.post(OPENAI_EMBEDDINGS_URL,
                    HttpClientUtil.toJson(body), headers);

            JsonObject json = HttpClientUtil.parseJson(responseStr);
            com.google.gson.JsonArray embeddingArray = json
                    .getAsJsonArray("data").get(0).getAsJsonObject()
                    .getAsJsonArray("embedding");

            List<Float> embedding = new ArrayList<>(embeddingArray.size());
            for (com.google.gson.JsonElement el : embeddingArray) {
                embedding.add(el.getAsFloat());
            }
            return embedding;

        } catch (Exception e) {
            LOG.error("Embedding failed for text snippet: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildMetadata(PageDocument doc) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("pagePath", doc.getPagePath());
        meta.put("title", nvl(doc.getTitle()));
        meta.put("description", nvl(doc.getDescription()));
        meta.put("category", nvl(doc.getCategory()));
        meta.put("archetype", nvl(doc.getArchetype()));
        meta.put("hasHero", doc.isHasHero());
        meta.put("hasVideo", doc.isHasVideo());
        meta.put("hasCta", doc.isHasCta());
        meta.put("heroTitle", nvl(doc.getHeroTitle()));
        meta.put("heroCTAText", nvl(doc.getHeroCTAText()));
        meta.put("componentCount", doc.getComponentCount());
        meta.put("pageViews", doc.getPageViews());
        meta.put("bounceRate", doc.getBounceRate());
        meta.put("avgTimeOnPage", doc.getAvgTimeOnPage());
        meta.put("heroCtr", doc.getHeroCtr());
        meta.put("conversionRate", doc.getConversionRate());
        meta.put("topTrafficSource", nvl(doc.getTopTrafficSource()));
        meta.put("topRegion", nvl(doc.getTopRegion()));
        meta.put("returningVisitorPct", doc.getReturningVisitorPct());
        if (doc.getTags() != null) {
            meta.put("tags", String.join(",", doc.getTags()));
        }
        if (doc.getComponents() != null) {
            meta.put("components", String.join(",", doc.getComponents()));
        }
        if (doc.getPublishedDate() != null) {
            meta.put("publishedDate", doc.getPublishedDate());
        }
        if (doc.getLastModified() != null) {
            meta.put("lastModified", doc.getLastModified());
        }
        return meta;
    }

    private String sanitizeId(String pagePath) {
        return pagePath.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}

package com.mycompany.core.services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.services.RAGQueryService;
import com.mycompany.core.utils.HttpClientUtil;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = RAGQueryService.class, immediate = true)
public class RAGQueryServiceImpl implements RAGQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(RAGQueryServiceImpl.class);
    private static final Gson GSON = new Gson();

    private static final String OPENAI_EMBEDDINGS_URL = "https://api.openai.com/v1/embeddings";
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    @Reference
    private AIConfigService config;

    @Reference
    private PineconeService pineconeService;

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> query(String question, String currentPagePath,
                                     List<Map<String, String>> conversationHistory) {
        try {
            // Step 1: Detect intent
            QueryIntent intent = detectIntent(question, currentPagePath);
            LOG.debug("Detected intent: {} for question: {}", intent.type, question);

            // Step 2: Embed the query
            List<Float> queryEmbedding = getEmbedding(question);
            if (queryEmbedding.isEmpty()) {
                return errorResponse("Failed to generate query embedding.");
            }

            // Step 3: Build Pinecone filter and search
            Map<String, Object> filter = buildFilter(intent, currentPagePath);
            List<Map<String, Object>> matches = pineconeService.queryDocuments(queryEmbedding, 10, filter);

            // Step 4: Build augmented prompt
            String systemPrompt = buildSystemPrompt(matches, currentPagePath, intent);

            // Step 5: Call LLM
            String answer = chat(systemPrompt, question, conversationHistory);

            // Step 6: Build response
            List<String> sources = extractSources(matches);
            Map<String, Object> result = new HashMap<>();
            result.put("answer", answer);
            result.put("sources", sources);
            result.put("intent", intent.type);
            return result;

        } catch (Exception e) {
            LOG.error("RAG query failed: {}", e.getMessage(), e);
            return errorResponse("An error occurred processing your question: " + e.getMessage());
        }
    }

    // ── Step 1: Intent Detection ──────────────────────────────────────────────

    private QueryIntent detectIntent(String question, String pagePath) {
        String intentPrompt = "Classify this content analytics question into exactly ONE type:\n"
                + "- page_specific: question about a single specific page\n"
                + "- site_wide: broad question about the whole site\n"
                + "- comparison: comparing multiple pages or segments\n"
                + "- trend: asks about trends or changes over time\n"
                + "- recommendation: asks for advice, suggestions, or best practices\n\n"
                + "Also extract the target_metric if any (e.g. bounce_rate, pageviews, conversion).\n\n"
                + "Respond ONLY with JSON: {\"type\":\"...\",\"target_metric\":\"...\",\"filters\":{}}\n\n"
                + "Question: " + question;

        try {
            String response = chat("You are a query intent classifier. Respond only with valid JSON.", intentPrompt, null);
            // Strip markdown code fences if present
            response = response.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonObject json = HttpClientUtil.parseJson(response);

            QueryIntent intent = new QueryIntent();
            intent.type = json.has("type") ? json.get("type").getAsString() : "site_wide";

            if (pagePath != null && !pagePath.isEmpty()
                    && ("page_specific".equals(intent.type) || question.toLowerCase().contains("this page"))) {
                intent.type = "page_specific";
            }
            return intent;
        } catch (Exception e) {
            LOG.warn("Intent detection failed, defaulting to site_wide: {}", e.getMessage());
            QueryIntent fallback = new QueryIntent();
            fallback.type = (pagePath != null && !pagePath.isEmpty()) ? "page_specific" : "site_wide";
            return fallback;
        }
    }

    // ── Step 2: Embedding ─────────────────────────────────────────────────────

    private List<Float> getEmbedding(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getOpenAiEmbeddingModel());
            body.put("input", text);
            body.put("dimensions", 1024);

            String response = HttpClientUtil.post(OPENAI_EMBEDDINGS_URL,
                    HttpClientUtil.toJson(body), openAiHeaders());

            JsonObject json = HttpClientUtil.parseJson(response);
            JsonArray embeddingArray = json.getAsJsonArray("data")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("embedding");

            List<Float> embedding = new ArrayList<>(embeddingArray.size());
            for (JsonElement el : embeddingArray) {
                embedding.add(el.getAsFloat());
            }
            return embedding;
        } catch (Exception e) {
            LOG.error("Embedding call failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── Step 3: Filter building ───────────────────────────────────────────────

    private Map<String, Object> buildFilter(QueryIntent intent, String pagePath) {
        Map<String, Object> filter = new HashMap<>();
        if ("page_specific".equals(intent.type) && pagePath != null && !pagePath.isEmpty()) {
            filter.put("pagePath", Collections.singletonMap("$eq", pagePath));
        }
        return filter.isEmpty() ? null : filter;
    }

    // ── Step 4: Prompt building ───────────────────────────────────────────────

    private String buildSystemPrompt(List<Map<String, Object>> matches, String pagePath, QueryIntent intent) {
        StringBuilder ctx = new StringBuilder();
        for (Map<String, Object> match : matches) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
            if (metadata != null) {
                ctx.append(GSON.toJson(metadata)).append("\n---\n");
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Content Intelligence Assistant for the WKND website built on Adobe Experience Manager.\n");
        prompt.append("You help content authors understand how their site content is performing and make data-driven decisions.\n\n");
        prompt.append("You have access to the following page data from the site:\n");
        prompt.append(ctx);

        if ("page_specific".equals(intent.type) && pagePath != null && !pagePath.isEmpty()) {
            prompt.append("\nThe author is currently editing the page: ").append(pagePath).append("\n");
            prompt.append("Focus your answer on this page unless the question is clearly about other pages.\n");
        }

        prompt.append("\nRules:\n");
        prompt.append("- Answer ONLY based on the data provided above. Do not make up data.\n");
        prompt.append("- Always mention specific page names and numbers when available.\n");
        prompt.append("- If comparing, use a clear format with metrics side by side.\n");
        prompt.append("- If recommending, base it on patterns you see in the top-performing pages.\n");
        prompt.append("- If the data doesn't contain enough info to answer, say so honestly.\n");
        prompt.append("- Keep answers concise but insightful. No fluff.\n");
        prompt.append("- When mentioning metrics, always include the actual numbers.\n");

        return prompt.toString();
    }

    // ── Step 5: Chat completion ───────────────────────────────────────────────

    private String chat(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        try {
            JsonArray messages = new JsonArray();

            // System message
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);

            // Conversation history (last 6 turns to stay within token budget)
            if (history != null) {
                int start = Math.max(0, history.size() - 6);
                for (int i = start; i < history.size(); i++) {
                    Map<String, String> turn = history.get(i);
                    JsonObject msg = new JsonObject();
                    msg.addProperty("role", turn.getOrDefault("role", "user"));
                    msg.addProperty("content", turn.getOrDefault("content", ""));
                    messages.add(msg);
                }
            }

            // Current user message
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);

            JsonObject body = new JsonObject();
            body.addProperty("model", config.getOpenAiChatModel());
            body.add("messages", messages);
            body.addProperty("temperature", 0.3);

            String response = HttpClientUtil.post(OPENAI_CHAT_URL, body.toString(), openAiHeaders());
            JsonObject json = HttpClientUtil.parseJson(response);

            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

        } catch (Exception e) {
            LOG.error("Chat completion failed: {}", e.getMessage(), e);
            return "I'm sorry, I couldn't process your question at this time. Please try again later.";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> extractSources(List<Map<String, Object>> matches) {
        List<String> sources = new ArrayList<>();
        for (Map<String, Object> match : matches) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
            if (metadata != null && metadata.containsKey("pagePath")) {
                String path = metadata.get("pagePath").toString();
                if (!sources.contains(path)) {
                    sources.add(path);
                }
            }
        }
        return sources;
    }

    private Map<String, String> openAiHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + config.getOpenAiApiKey());
        return headers;
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("answer", message);
        result.put("sources", Collections.emptyList());
        result.put("intent", "error");
        return result;
    }

    private static class QueryIntent {
        String type = "site_wide";
    }
}

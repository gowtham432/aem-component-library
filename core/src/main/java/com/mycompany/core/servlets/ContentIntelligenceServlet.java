package com.mycompany.core.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mycompany.core.services.RAGQueryService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POST /bin/api/contentIntelligence
 *
 * Request body (JSON):
 * {
 *   "question": "How is this page performing?",
 *   "pagePath": "/content/wknd/us/en/adventures/bali-surf-camp",
 *   "conversationHistory": [{"role":"user","content":"..."},{"role":"assistant","content":"..."}]
 * }
 *
 * Response body (JSON):
 * {
 *   "answer": "...",
 *   "sources": ["/content/wknd/..."],
 *   "intent": "page_specific"
 * }
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/api/contentIntelligence",
        "sling.servlet.methods=POST",
        "sling.auth.requirements=-/bin/api/contentIntelligence"
})
public class ContentIntelligenceServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new Gson();

    @Reference
    private RAGQueryService ragQueryService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        try {
            String body = request.getReader().lines()
                    .reduce("", (acc, line) -> acc + line);

            if (body == null || body.trim().isEmpty()) {
                sendError(response, 400, "Request body is required.");
                return;
            }

            JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();

            String question = getStringField(requestJson, "question");
            if (question == null || question.trim().isEmpty()) {
                sendError(response, 400, "Field 'question' is required.");
                return;
            }

            String pagePath = getStringField(requestJson, "pagePath");
            List<Map<String, String>> history = parseConversationHistory(requestJson);

            Map<String, Object> result = ragQueryService.query(question.trim(), pagePath, history);

            response.setStatus(200);
            response.getWriter().write(GSON.toJson(result));

        } catch (Exception e) {
            sendError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    private List<Map<String, String>> parseConversationHistory(JsonObject requestJson) {
        List<Map<String, String>> history = new ArrayList<>();
        if (!requestJson.has("conversationHistory")) return history;

        JsonArray arr = requestJson.getAsJsonArray("conversationHistory");
        for (JsonElement el : arr) {
            JsonObject turn = el.getAsJsonObject();
            Map<String, String> entry = new HashMap<>();
            entry.put("role", getStringField(turn, "role"));
            entry.put("content", getStringField(turn, "content"));
            history.add(entry);
        }
        return history;
    }

    private String getStringField(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private void sendError(SlingHttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        response.getWriter().write(err.toString());
    }
}

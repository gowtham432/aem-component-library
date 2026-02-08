package com.mycompany.core.services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mycompany.core.services.OpenAIService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = OpenAIService.class, immediate = true)
@Designate(ocd = OpenAIServiceImpl.Config.class)
public class OpenAIServiceImpl implements OpenAIService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIServiceImpl.class);
    private static final Gson GSON = new Gson();

    @ObjectClassDefinition(name = "OpenAI Service Configuration")
    public @interface Config {
        @AttributeDefinition(name = "OpenAI API Key")
        String openai_api_key() default "";

        @AttributeDefinition(name = "OpenAI API URL")
        String openai_api_url() default "https://api.openai.com/v1/chat/completions";

        @AttributeDefinition(name = "OpenAI Model")
        String openai_model() default "gpt-4o-mini";

        @AttributeDefinition(name = "Max Tokens")
        int openai_max_tokens() default 1000;

        @AttributeDefinition(name = "Temperature")
        double openai_temperature() default 0.3;
    }

    private String apiKey;
    private String apiUrl;
    private String model;
    private int maxTokens;
    private double temperature;

    @Activate
    protected void activate(Config config) {
        this.apiKey = config.openai_api_key();
        this.apiUrl = config.openai_api_url();
        this.model = config.openai_model();
        this.maxTokens = config.openai_max_tokens();
        this.temperature = config.openai_temperature();

        LOG.info("OpenAI Service activated with model: {}", model);
    }

    @Override
    public List<String> extractConcepts(String content) {
        String prompt = String.format(
                "Analyze the following content and extract key concepts, topics, and themes. " +
                        "Return ONLY a comma-separated list of concepts, no explanations:\n\n%s",
                content
        );

        String response = callOpenAI(prompt);
        return parseConceptsFromResponse(response);
    }

    @Override
    public List<String> generateTagSuggestions(String pageContent, Map<String, String> availableTags) {
        if (availableTags == null || availableTags.isEmpty()) {
            LOG.warn("No available tags provided to AI");
            return new ArrayList<>();
        }

        // Build formatted tag list
        StringBuilder tagList = new StringBuilder();
        tagList.append("AVAILABLE TAGS (you MUST return tag IDs from this list ONLY):\n\n");

        // Group by category for better readability
        Map<String, List<Map.Entry<String, String>>> categorized = categorizeTags(availableTags);

        for (Map.Entry<String, List<Map.Entry<String, String>>> categoryEntry : categorized.entrySet()) {
            String category = categoryEntry.getKey();
            tagList.append(category.toUpperCase().replace("-", " ")).append(":\n");

            for (Map.Entry<String, String> tag : categoryEntry.getValue()) {
                tagList.append("  - ").append(tag.getKey())
                        .append(" (").append(tag.getValue()).append(")\n");
            }
            tagList.append("\n");
        }

        String prompt = String.format(
                "You are a content tagging expert for an AEM (Adobe Experience Manager) system.\n\n" +
                        "%s" +
                        "INSTRUCTIONS:\n" +
                        "1. Analyze the content below\n" +
                        "2. Select ONLY the most relevant tag IDs from the available tags list above\n" +
                        "3. Return ONLY tag IDs, comma-separated, nothing else\n" +
                        "4. Do NOT invent new tags - use ONLY tags from the list\n" +
                        "5. Select 3-8 tags that best describe the content\n" +
                        "6. Prioritize content-type, topic, and audience tags\n\n" +
                        "CONTENT TO ANALYZE:\n" +
                        "---\n%s\n---\n\n" +
                        "Return format: tagid1,tagid2,tagid3",
                tagList.toString(),
                pageContent
        );

        LOG.debug("Sending prompt to OpenAI with {} available tags", availableTags.size());

        String response = callOpenAI(prompt);

        LOG.debug("OpenAI response: {}", response);

        return parseTagIdsFromResponse(response, availableTags);
    }

    @Override
    public String classifyContentType(String content) {
        String prompt = String.format(
                "Classify this content into ONE of these types: article, blog-post, product-launch, " +
                        "press-release, tutorial, landing-page, case-study, faq. " +
                        "Return ONLY the type name, nothing else:\n\n%s",
                content
        );

        String response = callOpenAI(prompt);
        return response.trim().toLowerCase();
    }

    /**
     * Categorize tags by their namespace category
     */
    private Map<String, List<Map.Entry<String, String>>> categorizeTags(Map<String, String> tags) {
        Map<String, List<Map.Entry<String, String>>> categorized = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String tagId = entry.getKey();
            String category = extractCategory(tagId);

            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        }

        return categorized;
    }

    /**
     * Extract category from tag ID (e.g., "myaemproject:content-type/article" -> "content-type")
     */
    private String extractCategory(String tagId) {
        if (tagId.contains(":")) {
            String afterColon = tagId.substring(tagId.indexOf(":") + 1);
            int slashIndex = afterColon.indexOf('/');
            if (slashIndex > 0) {
                return afterColon.substring(0, slashIndex);
            }
            return afterColon;
        }
        return "other";
    }

    /**
     * Core method to call OpenAI API
     */
    private String callOpenAI(String prompt) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(apiUrl);

            // Set headers
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + apiKey);

            // Build request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);

            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);

            requestBody.add("messages", messages);

            request.setEntity(new StringEntity(requestBody.toString()));

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());

                if (response.getStatusLine().getStatusCode() != 200) {
                    LOG.error("OpenAI API error: {}", responseBody);
                    return "";
                }

                // Parse response
                JsonObject jsonResponse = GSON.fromJson(responseBody, JsonObject.class);
                return jsonResponse
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content")
                        .getAsString();
            }

        } catch (IOException e) {
            LOG.error("Error calling OpenAI API", e);
            return "";
        }
    }

    /**
     * Parse comma-separated concepts from AI response
     */
    private List<String> parseConceptsFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Parse and validate tag IDs from AI response
     */
    private List<String> parseTagIdsFromResponse(String response, Map<String, String> availableTags) {
        if (response == null || response.trim().isEmpty()) {
            LOG.warn("Empty response from OpenAI");
            return new ArrayList<>();
        }

        // Clean up response - remove any markdown, quotes, etc.
        String cleaned = response.replace("```", "")
                .replace("`", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();

        List<String> suggestedTagIds = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // Validate that suggested tags actually exist
        List<String> validTags = new ArrayList<>();

        for (String tagId : suggestedTagIds) {
            if (availableTags.containsKey(tagId)) {
                validTags.add(tagId);
            } else {
                LOG.warn("AI suggested non-existent tag: {}", tagId);
            }
        }

        if (validTags.isEmpty()) {
            LOG.warn("AI returned no valid tags. Response was: {}", response);
        } else {
            LOG.info("AI suggested {} valid tags: {}", validTags.size(), validTags);
        }

        return validTags;
    }
}

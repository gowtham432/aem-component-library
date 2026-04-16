package com.mycompany.core.services.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mycompany.core.services.AIConfigService;
import com.mycompany.core.services.PineconeService;
import com.mycompany.core.utils.HttpClientUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = PineconeService.class, immediate = true)
public class PineconeServiceImpl implements PineconeService {

    private static final Logger LOG = LoggerFactory.getLogger(PineconeServiceImpl.class);

    private String pineconeApiKey;
    private String pineconeIndexName;
    private String pineconeBaseUrl;

    @Reference
    private AIConfigService config;

    @Activate
    protected void activate() {
        this.pineconeApiKey = config.getPineconeApiKey();
        this.pineconeIndexName = config.getPineconeIndexName();
        this.pineconeBaseUrl = config.getPineconeBaseUrl();
        LOG.info("Pinecone Service activated with index: {}", pineconeIndexName);
    }

    @Override
    public boolean upsertDocument(String id, List<Float> embedding, Map<String, Object> metadata) {
        try {
            Map<String, Object> vector = new HashMap<>();
            vector.put("id", id);
            vector.put("values", embedding);
            vector.put("metadata", metadata);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vectors", Collections.singletonList(vector));

            String jsonBody = HttpClientUtil.toJson(requestBody);

            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            String url = pineconeBaseUrl + "/vectors/upsert";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                LOG.debug("Successfully upserted document: {}", id);
                return true;
            } else {
                LOG.error("Failed to upsert document: {}", id);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Error upserting document {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> queryDocuments(List<Float> queryEmbedding, int topK, Map<String, Object> filter) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", queryEmbedding);
            requestBody.put("topK", topK);
            requestBody.put("includeMetadata", true);

            if (filter != null && !filter.isEmpty()) {
                requestBody.put("filter", filter);
            }

            String jsonBody = HttpClientUtil.toJson(requestBody);

            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            String url = pineconeBaseUrl + "/query";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                return parseQueryResults(HttpClientUtil.parseJson(response));
            } else {
                LOG.error("Failed to query documents");
                return Collections.emptyList();
            }

        } catch (Exception e) {
            LOG.error("Error querying documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean deleteDocuments(List<String> ids) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", ids);

            String jsonBody = HttpClientUtil.toJson(requestBody);

            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            String url = pineconeBaseUrl + "/vectors/delete";
            String response = HttpClientUtil.post(url, jsonBody, headers);

            if (response != null) {
                LOG.debug("Successfully deleted documents: {}", ids);
                return true;
            } else {
                LOG.error("Failed to delete documents: {}", ids);
                return false;
            }

        } catch (Exception e) {
            LOG.error("Error deleting documents {}: {}", ids, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Map<String, Object> describeIndexStats() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Api-Key", pineconeApiKey);

            String url = pineconeBaseUrl + "/describe_index_stats";
            String response = HttpClientUtil.post(url, "{}", headers);

            if (response != null) {
                JsonObject json = HttpClientUtil.parseJson(response);
                Map<String, Object> stats = new HashMap<>();

                if (json.has("totalVectorCount")) {
                    stats.put("totalVectorCount", json.get("totalVectorCount").getAsLong());
                }
                if (json.has("dimension")) {
                    stats.put("dimension", json.get("dimension").getAsInt());
                }

                return stats;
            } else {
                LOG.error("Failed to get index stats");
                return Collections.emptyMap();
            }

        } catch (Exception e) {
            LOG.error("Error getting index stats: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> parseQueryResults(JsonObject responseNode) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            if (!responseNode.has("matches")) {
                return results;
            }

            JsonArray matches = responseNode.getAsJsonArray("matches");
            for (JsonElement matchEl : matches) {
                JsonObject match = matchEl.getAsJsonObject();
                Map<String, Object> result = new HashMap<>();

                if (match.has("id")) {
                    result.put("id", match.get("id").getAsString());
                }
                if (match.has("score")) {
                    result.put("score", match.get("score").getAsDouble());
                }
                if (match.has("metadata")) {
                    JsonObject metadata = match.getAsJsonObject("metadata");
                    Map<String, Object> metadataMap = new HashMap<>();

                    for (Map.Entry<String, JsonElement> entry : metadata.entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value.isJsonPrimitive()) {
                            if (value.getAsJsonPrimitive().isBoolean()) {
                                metadataMap.put(entry.getKey(), value.getAsBoolean());
                            } else if (value.getAsJsonPrimitive().isNumber()) {
                                metadataMap.put(entry.getKey(), value.getAsDouble());
                            } else {
                                metadataMap.put(entry.getKey(), value.getAsString());
                            }
                        } else {
                            metadataMap.put(entry.getKey(), value.toString());
                        }
                    }

                    result.put("metadata", metadataMap);
                }

                results.add(result);
            }
        } catch (Exception e) {
            LOG.error("Error parsing query results: {}", e.getMessage(), e);
        }

        return results;
    }
}

package com.mycompany.core.services;

import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Pinecone vector database.
 */
public interface PineconeService {

    /**
     * Upsert a document vector into Pinecone.
     *
     * @param id       unique vector ID (e.g. hashed page path)
     * @param embedding the float vector (1536 dims for text-embedding-3-small)
     * @param metadata  key/value metadata stored alongside the vector
     * @return true if upsert succeeded
     */
    boolean upsertDocument(String id, List<Float> embedding, Map<String, Object> metadata);

    /**
     * Query Pinecone for the top-K nearest vectors.
     *
     * @param queryEmbedding the query vector
     * @param topK           number of results to return
     * @param filter         optional metadata filter (Pinecone filter syntax)
     * @return list of matches, each containing id, score, and metadata
     */
    List<Map<String, Object>> queryDocuments(List<Float> queryEmbedding, int topK, Map<String, Object> filter);

    /**
     * Delete vectors by their IDs.
     *
     * @param ids list of vector IDs to delete
     * @return true if delete succeeded
     */
    boolean deleteDocuments(List<String> ids);

    /**
     * Get index statistics (total vector count, etc.).
     *
     * @return map with index stats
     */
    Map<String, Object> describeIndexStats();
}

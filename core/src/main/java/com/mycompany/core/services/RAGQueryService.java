package com.mycompany.core.services;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full RAG (Retrieval Augmented Generation) pipeline:
 * intent detection → embedding → Pinecone search → LLM answer.
 */
public interface RAGQueryService {

    /**
     * Process a natural language question and return an AI-generated answer
     * grounded in the retrieved page data.
     *
     * @param question            the author's question
     * @param currentPagePath     the page the author is currently editing (may be null)
     * @param conversationHistory list of prior turns [{role, content}]
     * @return map with keys: "answer" (String), "sources" (List<String>), "intent" (String)
     */
    Map<String, Object> query(String question, String currentPagePath, List<Map<String, String>> conversationHistory);
}
